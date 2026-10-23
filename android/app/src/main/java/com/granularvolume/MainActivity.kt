package com.granularvolume

import android.Manifest
import android.app.TimePickerDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.granularvolume.audio.StreamVolumeController
import com.granularvolume.service.VolumeControlService
import com.granularvolume.sleep.*
import com.granularvolume.util.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The fork's permanent home: light, schedule, and sound in one place. */
class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private var status: TextView? = null
    private var statusDetail: TextView? = null
    private var soundValue: TextView? = null
    private var soundSlider: Slider? = null
    private var soundHint: TextView? = null
    private var displayHint: TextView? = null
    private var resumeDimming: Button? = null
    private var adjustingSound = false
    private var pendingEnable = false
    private var locationRequest: CancellationSignal? = null
    private val reconcile = Runnable { SleepScheduler.reconcile(this); refreshStatus() }
    private val refresh = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 2000) }
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingEnable && PermissionHelper.canDrawOverlays(this)) enable()
        else { pendingEnable = false; render() }
    }
    private val timingPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingEnable && SleepScheduler.hasTimingPermission(this)) enable()
        else { pendingEnable = false; SleepScheduler.reconcile(this); render() }
    }
    private val brightnessPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        SleepScheduler.reconcile(this); render()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) locate() else toast("You can enter a location manually instead")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        window.statusBarColor = NightColors.background; window.navigationBarColor = NightColors.background
        window.decorView.systemUiVisibility = 0
        scroll = ScrollView(this).apply { setBackgroundColor(NightColors.background); isFillViewport = true; clipToPadding = false }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(32)) }
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            scroll.addView(root, android.widget.FrameLayout.LayoutParams(dp(560), -2, Gravity.CENTER_HORIZONTAL))
        } else scroll.addView(root)
        setContentView(scroll)
        render()
    }
    override fun onResume() {
        super.onResume(); SleepScheduler.reconcile(this)
        if (::root.isInitialized) render()
        handler.removeCallbacks(refresh); handler.post(refresh)
    }
    override fun onPause() {
        // Do not lose a pending schedule edit when the user immediately leaves the screen.
        handler.removeCallbacks(reconcile); SleepScheduler.reconcile(this)
        handler.removeCallbacks(refresh); super.onPause()
    }
    override fun onDestroy() {
        locationRequest?.cancel(); handler.removeCallbacksAndMessages(null); super.onDestroy()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f, color: Int = NightColors.text, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun panel(color: Int = NightColors.surface, radius: Int = 24) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), NightColors.stroke)
    }
    private fun LinearLayout.add(view: View, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun card(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = panel(); setPadding(dp(20), dp(20), dp(20), dp(16))
        add(text(title, 20f, bold = true)); add(text(subtitle, 13f, NightColors.secondary), 5)
        root.add(this, 16)
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(50); minimumWidth = 0
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextColor(if (primary) NightColors.background else NightColors.lavender)
        background = panel(if (primary) NightColors.amber else NightColors.surface, 16)
        setOnClickListener { action() }
    }
    private fun persist(redraw: Boolean = false, change: (SleepConfig) -> SleepConfig) {
        SleepSettings.update(this, change)
        handler.removeCallbacks(reconcile); handler.postDelayed(reconcile, 150)
        if (redraw) render()
    }
    private fun formatTime(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(
        DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", Locale.getDefault()))
    private fun slider(parent: LinearLayout, label: String, value: Int, max: Int, unit: String = "%", step: Float = 1f,
                       color: Int = NightColors.amber, changed: (Int) -> Unit): Pair<Slider, TextView> {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(label, 14f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val amount = text("$value$unit", 18f, color, true); row.addView(amount); parent.add(row, 19)
        val s = Slider(this).apply {
            valueFrom = 0f; valueTo = max.toFloat(); stepSize = step; this.value = value.toFloat()
            isTickVisible = false
            contentDescription = label
            thumbTintList = ColorStateList.valueOf(color); trackActiveTintList = ColorStateList.valueOf(color)
            trackInactiveTintList = ColorStateList.valueOf(NightColors.stroke)
            setLabelFormatter { "${it.toInt()}$unit" }
            addOnChangeListener { _, n, user -> if (user) { amount.text = "${n.toInt()}$unit"; changed(n.toInt()) } }
        }
        parent.add(s); return s to amount
    }
    private fun toggle(parent: LinearLayout, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.add(text(title, 15f, bold = true)); labels.add(text(subtitle, 12f, NightColors.secondary), 3)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        row.addView(SwitchMaterial(this).apply {
            setShowText(false); textOn = ""; textOff = ""; isUseMaterialThemeColors = false
            minHeight = dp(48); switchMinWidth = dp(52)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(NightColors.amber, NightColors.lavender))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFF946B45.toInt(), 0xFF6A5C78.toInt()))
            isChecked = checked; isEnabled = enabled; contentDescription = title
            setOnCheckedChangeListener { _, value -> changed(value) }
        }); row.alpha = if (enabled) 1f else .45f; parent.add(row, 5)
    }
    private fun render() {
        val y = scroll.scrollY; root.removeAllViews(); soundValue = null; soundSlider = null; soundHint = null
        val config = SleepSettings.read(this)
        root.add(text("QUIET NIGHT", 12f, NightColors.lavender, true).apply { letterSpacing = .22f })
        val hero = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(text("Make room\nfor rest.", 36f).apply { typeface = Typeface.create("serif", Typeface.NORMAL) }, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(MoonView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(108), dp(134)))
        root.add(hero, 6)
        root.add(text("Softer light. Quieter sound. Your evening, at ease.", 13f, NightColors.secondary), 2)
        val state = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = panel(0xFF272030.toInt()); setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        status = text("", 19f, NightColors.text, true); statusDetail = text("", 13f, NightColors.secondary)
        state.add(status!!); state.add(statusDetail!!, 4)
        state.add(button(if (config.enabled) "Pause wind-down" else "Begin wind-down", true) {
            if (SleepSettings.read(this).enabled) {
                SleepSettings.update(this) { it.copy(enabled = false) }; SleepScheduler.reconcile(this); render()
            } else enable()
        }, 14)
        root.add(state, 22)
        if (!PermissionHelper.canDrawOverlays(this)) {
            val setup = card("Let the evening follow you", "Allow Quiet Night to soften the screen over other apps.")
            setup.add(button("Allow screen overlay") { overlayPermission.launch(PermissionHelper.overlayPermissionIntent(this)) }, 12)
        }
        val light = card("Evening light", "A red-orange tint with less blue, paired with gentle dimming.")
        slider(light, "Color intensity", config.warmth, 100) { n -> persist { it.copy(warmth = n) } }
        slider(light, "Screen dim", config.dim, 85, color = NightColors.lavender) { n -> persist { it.copy(dim = n) } }
        light.add(text("Set either slider to 0% to turn that adjustment off. Dimming is capped at 85%.", 12f, NightColors.secondary), 2)
        displayHint = text("", 12f, NightColors.secondary)
        light.add(displayHint!!, 9)
        if (!ScreenDimming.canWrite(this) && (config.dim > 0 || ScreenDimming.needsRestore(this))) {
            light.add(button("Enable system-wide dimming") {
                brightnessPermission.launch(ScreenDimming.permissionIntent(this))
            }, 12)
            light.add(text("Dims Quick Settings, notifications, and the interactive lock screen. Saves your brightness and adaptive mode for restoration on stop; manual changes take precedence. Without access, only app screens are dimmed.", 12f, NightColors.secondary), 6)
        }
        resumeDimming = button("Resume screen dimming") {
            ScreenDimming.resume(this); SleepScheduler.reconcile(this); render()
        }.apply { visibility = View.GONE }
        light.add(resumeDimming!!, 8)
        val schedule = card("Your evening rhythm", "Choose when your light and sound settings are active.")
        val modes = LinearLayout(this)
        for ((mode, name) in listOf(ScheduleMode.ALWAYS to "Always", ScheduleMode.SUN to "Sunset", ScheduleMode.CUSTOM to "Custom")) {
            modes.addView(button(name, config.mode == mode) { persist(true) { it.copy(mode = mode) } },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (mode != ScheduleMode.ALWAYS) marginStart = dp(6) })
        }
        schedule.add(modes, 16)
        when (config.mode) {
            ScheduleMode.ALWAYS -> schedule.add(text("Stays on until you pause. No schedule access needed.", 13f, NightColors.secondary), 14)
            ScheduleMode.SUN -> {
                schedule.add(text("Sunset → sunrise", 18f, NightColors.amber, true), 18)
                schedule.add(text(if (config.latitude == null) "Choose a location once. Sun times update locally each day." else
                    "Using ${"%.2f".format(config.latitude)}°, ${"%.2f".format(config.longitude)}°. No background location tracking.", 12f, NightColors.secondary), 5)
                schedule.add(button("Use current location") { requestLocation() }, 12)
                schedule.add(button("Enter location manually") { locationDialog() }, 6)
            }
            ScheduleMode.CUSTOM -> {
                val dial = SleepWindowDial(this).apply { start = config.start; end = config.end }
                schedule.addView(dial, LinearLayout.LayoutParams(-1, dp(265)).apply { topMargin = dp(10) })
                val times = LinearLayout(this)
                val startButton = button("START\n${formatTime(config.start)}") { pickTime(true) }
                val endButton = button("END\n${formatTime(config.end)}") { pickTime(false) }
                times.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                times.addView(endButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                schedule.add(times, 4)
                dial.onChange = { start, end ->
                    startButton.text = "START\n${formatTime(start)}"; endButton.text = "END\n${formatTime(end)}"
                    persist { it.copy(start = start, end = end) }
                }
                schedule.add(text("Drag either handle, or tap a time for precise control. Overnight schedules are supported.", 12f, NightColors.secondary), 12)
            }
        }
        if (config.mode != ScheduleMode.ALWAYS && !SleepScheduler.hasTimingPermission(this)) {
            schedule.add(button("Allow scheduled changes") { openTimingPermission() }, 14)
            schedule.add(text("Let Android start and stop wind-down at your chosen times, even when the app is closed.", 12f, NightColors.secondary), 6)
        }
        val sound = card("A little quieter", "Keep the quiet steps below Android’s usual media minimum.")
        toggle(sound, "Quiet Dial", "Enable finer media-volume control during wind-down.", config.quiet) { checked -> persist(true) { it.copy(quiet = checked) } }
        toggle(sound, "Quiet steps with volume-down", "At the lowest step, keep reducing sound instead of muting. Use Mute for silence.", config.volumeDown, config.quiet) { checked -> persist { it.copy(volumeDown = checked) } }
        if (config.quiet) {
            val active = ControlLive.running && SleepSchedule.plan(config).active
            val gain = if (Prefs.isLevelInQuietZone(this)) ((-Prefs.getAttenuation(this) / 5).toInt() * 5).coerceIn(0, 30) else 0
            val (s, value) = slider(sound, "Extra attenuation", gain, 30, " dB", 5f, NightColors.lavender) {
                command(VolumeControlService.ACTION_SET_GAIN) { putExtra("db", -it.toFloat()) }
            }
            s.isEnabled = active; soundSlider = s; soundValue = value
            s.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) { adjustingSound = true }
                override fun onStopTrackingTouch(slider: Slider) { adjustingSound = false }
            })
            soundHint = text("", 12f, NightColors.secondary)
            sound.add(soundHint!!, 4)
            sound.add(button("Mute / unmute media") { command(VolumeControlService.ACTION_MUTE) }.apply {
                isEnabled = active; alpha = if (active) 1f else .45f
            }, 12)
            toggle(sound, "Floating controls", "A compact − / + control over other apps.", config.bubble) { checked -> persist { it.copy(bubble = checked) } }
            bluetoothCalibration(sound, active)
        }
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        footer.addView(button("About & privacy") { about() }, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(button("Licenses") { startActivity(Intent(this, LicensesActivity::class.java)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        root.add(footer, 22)
        root.add(text("Less light is the goal. Put your phone away when you’re ready to sleep.", 12f, NightColors.secondary).apply { gravity = Gravity.CENTER }, 8)
        refreshStatus(); scroll.post { scroll.scrollTo(0, y) }
    }
    private fun refreshStatus() {
        val config = SleepSettings.read(this); val plan = SleepSchedule.plan(config)
        status?.text = when {
            !config.enabled -> "Ready when you are"
            !SleepScheduler.ready(this, config) -> "One more step to get ready"
            plan.message != null -> "Your schedule needs a detail"
            plan.active && ControlLive.running -> "Wind-down is on"
            plan.active -> "Starting your evening settings"
            else -> "Your evening is scheduled"
        }
        statusDetail?.text = when {
            !config.enabled -> "Your light and sound settings are saved."
            !SleepScheduler.ready(this, config) -> "Allow the access shown below to activate your settings."
            plan.message != null -> plan.message
            plan.nextChange != null -> {
                val local = plan.nextChange.atZone(ZoneId.systemDefault())
                "${if (plan.active) "Until" else "Begins"} ${formatTime(local.hour * 60 + local.minute)} · ${if (config.mode == ScheduleMode.SUN) "sunset to sunrise" else "your schedule"}"
            }
            else -> "Until you pause · ${config.dim}% dim · ${config.warmth}% warmth"
        }
        if (!adjustingSound) {
            val gain = if (Prefs.isLevelInQuietZone(this)) (-Prefs.getAttenuation(this)).coerceIn(0f, 30f) else 0f
            soundSlider?.let { if (kotlin.math.abs(it.value - gain) > .1f) it.value = (gain / 5).toInt() * 5f }
            soundValue?.text = "${gain.toInt()} dB"
        }
        val userControl = ScreenDimming.userHasControl(this)
        resumeDimming?.visibility = if (userControl && config.enabled && config.dim > 0) View.VISIBLE else View.GONE
        displayHint?.text = when {
            ScreenDimming.needsRestore(this) && !ScreenDimming.canWrite(this) -> "Allow settings access to restore your saved brightness. You can also adjust it in Quick Settings."
            config.dim == 0 -> "Screen dimming is off."
            userControl -> "You changed system brightness. That setting is left alone until you resume dimming or restart wind-down."
            !config.enabled || !plan.active -> if (ScreenDimming.canWrite(this)) "System-wide dimming is ready for your next wind-down." else "App-only dimming. Enable system-wide dimming to include system screens."
            ControlLive.dimming == ScreenDimming.Mode.SYSTEM -> "System-wide dimming is on. Your previous brightness and adaptive mode return when you pause."
            ControlLive.dimming == ScreenDimming.Mode.RESTORE_NEEDED -> "Your saved brightness needs restoring. Check system-settings access."
            else -> "App-only dimming. System brightness is unavailable on this device or access has not been granted."
        }
        soundHint?.text = when {
            !ControlLive.running || !plan.active -> "Begin wind-down to adjust the sound."
            ControlLive.effect == ControlLive.Effect.NONE -> "This device did not provide an audio effect. Quiet steps may not change the sound."
            StreamVolumeController(this).inCellularCall() -> "Quiet steps cannot change cellular-call audio."
            ControlLive.effect == ControlLive.Effect.FALLBACK -> "Compatibility audio mode. Attenuation support varies by device."
            else -> "Attenuation below the audible minimum. Audio-effect support varies by device."
        }
    }
    private fun enable() {
        pendingEnable = true
        if (!PermissionHelper.canDrawOverlays(this)) { overlayPermission.launch(PermissionHelper.overlayPermissionIntent(this)); return }
        if (SleepSettings.read(this).mode != ScheduleMode.ALWAYS && !SleepScheduler.hasTimingPermission(this)) { openTimingPermission(); return }
        pendingEnable = false
        SleepSettings.update(this) { it.copy(enabled = true) }; SleepScheduler.reconcile(this); render()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun openTimingPermission() {
        if (Build.VERSION.SDK_INT >= 31) timingPermission.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }
    private fun pickTime(start: Boolean) {
        val cfg = SleepSettings.read(this); val time = if (start) cfg.start else cfg.end
        TimePickerDialog(this, { _, hour, minute ->
            val value = hour * 60 + minute
            if (value == if (start) cfg.end else cfg.start) toast("Choose different start and end times")
            else persist(true) { if (start) it.copy(start = value) else it.copy(end = value) }
        }, time / 60, time % 60, android.text.format.DateFormat.is24HourFormat(this)).show()
    }
    private fun command(action: String, extras: Intent.() -> Unit = {}) {
        if (!ControlLive.running || !SleepSettings.read(this).quiet) { toast("Turn on wind-down and Quiet Dial first"); return }
        startService(Intent(this, VolumeControlService::class.java).setAction(action).apply(extras))
    }
    private fun bluetoothCalibration(parent: LinearLayout, active: Boolean) {
        val volume = StreamVolumeController(this)
        if (!active || volume.inCall()) return
        val name = volume.activeBluetoothName() ?: return
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val min = maxOf(1, am.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)); val max = volume.calibrationMaxIndex()
        parent.add(button("Headphone setup · $name") {
            var selected = Prefs.getBluetoothFloor(this, name).coerceIn(min, max)
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
            body.add(text("With audio playing, find the first step you can hear. Previews use normal gain and are limited to steps $min–$max. The setting is saved for this headset name.", 14f, NightColors.secondary))
            val value = text("Step $selected", 24f, NightColors.amber, true).apply { gravity = Gravity.CENTER }
            body.add(value, 16)
            val row = LinearLayout(this); val down = button("−") {}; val up = button("+") {}
            fun preview(delta: Int) {
                if (volume.activeBluetoothName() != name || volume.inCall()) { toast("The audio route changed; reopen setup"); return }
                selected = (selected + delta).coerceIn(min, max); value.text = "Step $selected"
                down.isEnabled = selected > min; up.isEnabled = selected < max
                command(VolumeControlService.ACTION_CALIBRATE_BLUETOOTH_FLOOR) {
                    putExtra(VolumeControlService.EXTRA_BLUETOOTH_NAME, name); putExtra(VolumeControlService.EXTRA_BLUETOOTH_FLOOR, selected)
                }
            }
            down.setOnClickListener { preview(-1) }; up.setOnClickListener { preview(1) }
            down.isEnabled = selected > min; up.isEnabled = selected < max
            row.addView(down, LinearLayout.LayoutParams(0, dp(50), 1f)); row.addView(up, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(12) })
            body.add(row, 14)
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("First audible step").setView(body).setPositiveButton("Done", null).show()
        }, 10)
    }
    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    private fun locate() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val providers = manager.getProviders(true).filter {
                it != LocationManager.GPS_PROVIDER || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (last != null && System.currentTimeMillis() - last.time < 86_400_000) {
                persist(true) { it.copy(latitude = last.latitude, longitude = last.longitude) }; return
            }
            val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            if (provider == null) { toast("Turn on location, or enter coordinates manually"); return }
            locationRequest?.cancel(); val cancellation = CancellationSignal(); locationRequest = cancellation
            toast("Finding your location…")
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(this)) { location ->
                if (location == null) toast("Location unavailable; you can enter it manually")
                else if (!isDestroyed) persist(true) { it.copy(latitude = location.latitude, longitude = location.longitude) }
            }
            handler.postDelayed({ cancellation.cancel() }, 30_000)
        } catch (_: SecurityException) { toast("Location access was not granted") }
    }
    private fun locationDialog() {
        val cfg = SleepSettings.read(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        fun input(hintText: String, value: Double?) = EditText(this).apply {
            hint = hintText; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value?.toString() ?: ""); setSingleLine()
        }
        val lat = input("Latitude (−90 to 90)", cfg.latitude); val lon = input("Longitude (−180 to 180)", cfg.longitude)
        form.add(lat); form.add(lon, 8)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Sun times location").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val a = lat.text.toString().toDoubleOrNull(); val b = lon.text.toString().toDoubleOrNull()
            if (a == null || b == null || !a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0) {
                toast("Enter valid latitude and longitude"); return@setOnClickListener
            }
            persist(true) { it.copy(latitude = a, longitude = b) }; dialog.dismiss()
        } }; dialog.show()
    }
    private fun about() {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("A quieter kind of evening")
            .setMessage("Quiet Night is a free, open-source fork of Granular Volume by Rzuss.\n\nEverything stays on your device. No ads, accounts, tracking, or internet permission. Location is optional and saved only to calculate sun times; there is no background location tracking.\n\nDimming bright light and reducing blue light can support an evening routine. A screen tint is not a sleep treatment. System-wide dimming uses device brightness; warm tint is limited to app screens. Some protected screens hide overlays, and always-on displays have separate brightness rules.\n\nGPL-3.0. Source: github.com/JaredTweed/granular-volume")
            .setNeutralButton("Source code") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JaredTweed/granular-volume"))) }
                    .onFailure { toast("Source: github.com/JaredTweed/granular-volume") }
            }.setPositiveButton("Done", null).show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
