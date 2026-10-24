package com.granularvolume

import android.Manifest
import android.app.TimePickerDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
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
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.color.DynamicColors
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var actions: LinearLayout
    private var screenAction: ExtendedFloatingActionButton? = null
    private var audioAction: ExtendedFloatingActionButton? = null
    private var renderedAudioReady = false
    private var renderedSystemOverlay = false
    private var quietIndicator: QuietLevelIndicator? = null
    private var quietHint: TextView? = null
    private var locationRequest: CancellationSignal? = null
    private val reconcile = Runnable { SleepScheduler.reconcile(this); refreshStatus() }
    private val refresh = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 250) }
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        SleepScheduler.reconcile(this)
        if (SleepSettings.read(this).screen && PermissionHelper.canDrawOverlays(this)) continueScreenAccess()
        render()
    }
    private val timingPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        SleepScheduler.reconcile(this); render()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) locate() else toast("You can enter a location manually instead")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        NightColors.applyTheme(this)
        window.statusBarColor = NightColors.background; window.navigationBarColor = NightColors.background
        window.decorView.systemUiVisibility = 0
        WindowCompat.setDecorFitsSystemWindows(window, false)
        scroll = ScrollView(this).apply { setBackgroundColor(NightColors.background); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(112)) }
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            scroll.addView(root, FrameLayout.LayoutParams(dp(560), -2, Gravity.CENTER_HORIZONTAL))
        } else scroll.addView(root)
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(NightColors.surface); cornerRadius = dp(32).toFloat()
            }
        }
        val frame = FrameLayout(this).apply { setBackgroundColor(NightColors.background) }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(actions, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            (actions.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = bars.left + dp(20); rightMargin = bars.right + dp(20); bottomMargin = bars.bottom + dp(16)
            }
            insets
        }
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
    private fun LinearLayout.add(view: View, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun card(title: String): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(16))
            add(text(title, 18f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        }
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat(); cardElevation = 0f
            setCardBackgroundColor(NightColors.surface); strokeColor = NightColors.stroke; strokeWidth = 0
            addView(content)
        }
        root.add(card, 12)
        return content
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit): Button = MaterialButton(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(50); minimumWidth = 0
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextColor(if (primary) NightColors.background else NightColors.lavender)
        backgroundTintList = ColorStateList.valueOf(if (primary) NightColors.amber else NightColors.surface)
        cornerRadius = dp(24); strokeWidth = if (primary) 0 else dp(1)
        strokeColor = ColorStateList.valueOf(NightColors.stroke)
        setOnClickListener { action() }
    }
    private fun actionButton(label: String, icon: Int, active: Boolean, description: String, action: () -> Unit) =
        ExtendedFloatingActionButton(this).apply {
            text = label; contentDescription = description
            isAllCaps = false; textSize = 14f
            setIconResource(icon); minimumWidth = dp(126)
            setTextColor(if (active) NightColors.background else NightColors.text)
            iconTint = ColorStateList.valueOf(if (active) NightColors.background else NightColors.lavender)
            backgroundTintList = ColorStateList.valueOf(if (active) NightColors.amber else NightColors.surface)
            elevation = 0f; cornerRadius = dp(24)
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
    private fun render() {
        val y = scroll.scrollY
        root.removeAllViews(); actions.removeAllViews()
        quietIndicator = null; quietHint = null
        screenAction = null; audioAction = null
        val config = SleepSettings.read(this)
        val plan = SleepSchedule.plan(config)
        renderedAudioReady = ControlLive.audioState != null
        renderedSystemOverlay = ScreenOverlayService.isConnected()

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("Quiet Night", 30f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("") {}.apply {
            contentDescription = "More options"
            (this as MaterialButton).setIconResource(R.drawable.ic_more)
            iconPadding = 0; iconTint = ColorStateList.valueOf(NightColors.secondary)
            setPadding(dp(12), 0, dp(12), 0)
            strokeWidth = 0; backgroundTintList = ColorStateList.valueOf(NightColors.background)
            setOnClickListener { moreOptions(this) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.add(header, 4)

        val light = card("Screen")
        slider(light, "Warmth", config.warmth, 100) { n -> persist { it.copy(warmth = n) } }
        slider(light, "Dim", config.dim, 85, color = NightColors.lavender) { n -> persist { it.copy(dim = n) } }
        if (!ScreenOverlayService.isConnected()) {
            light.add(button("Include Quick Settings") { screenOverlayHelp() }, 6)
        }
        if (!ScreenOverlayService.isConnected() && !PermissionHelper.canDrawOverlays(this)) {
            light.add(button("Allow screen overlay") {
                overlayPermission.launch(PermissionHelper.overlayPermissionIntent(this))
            }, 4)
        }
        if (LegacyBrightnessRecovery.pending(this)) {
            light.add(button("Restore previous brightness") {
                timingPermission.launch(LegacyBrightnessRecovery.permissionIntent(this))
            }, 6)
        }

        val schedule = card("Screen schedule")
        val modes = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true; isSelectionRequired = true
        }
        val modeIds = mutableMapOf<Int, ScheduleMode>()
        for ((mode, name) in listOf(ScheduleMode.ALWAYS to "Always", ScheduleMode.SUN to "Sunset", ScheduleMode.CUSTOM to "Custom")) {
            val choice = (button(name, config.mode == mode) {} as MaterialButton).apply {
                id = View.generateViewId(); isCheckable = true
                setPadding(dp(8), dp(8), dp(8), dp(8))
                insetTop = 0; insetBottom = 0
                strokeWidth = dp(1)
                strokeColor = ColorStateList.valueOf(if (config.mode == mode) NightColors.amber else NightColors.stroke)
            }
            modeIds[choice.id] = mode
            modes.addView(choice, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (config.mode == mode) modes.check(choice.id)
        }
        modes.addOnButtonCheckedListener { _, id, checked ->
            if (checked) modeIds[id]?.takeIf { it != config.mode }?.let { selected ->
                persist(true) { it.copy(mode = selected) }
            }
        }
        schedule.add(modes, 12)
        when (config.mode) {
            ScheduleMode.ALWAYS -> Unit
            ScheduleMode.SUN -> {
                schedule.add(text("Sunset → sunrise", 14f, NightColors.secondary), 14)
                if (config.latitude != null) {
                    schedule.add(button("Change location") {
                        MaterialAlertDialogBuilder(this).setTitle("Location")
                            .setItems(arrayOf("Use current location", "Enter coordinates")) { _, which ->
                                if (which == 0) requestLocation() else locationDialog()
                            }.show()
                    }, 6)
                } else {
                    val locations = LinearLayout(this)
                    locations.addView(button("Use location") { requestLocation() }, LinearLayout.LayoutParams(0, -2, 1f))
                    locations.addView(button("Enter manually") { locationDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
                    schedule.add(locations, 6)
                }
                plan.message?.let { schedule.add(text(it, 12f, NightColors.secondary), 8) }
            }
            ScheduleMode.CUSTOM -> {
                val dial = SleepWindowDial(this).apply { start = config.start; end = config.end }
                schedule.addView(dial, LinearLayout.LayoutParams(-1, dp(232)).apply { topMargin = dp(8) })
                val times = LinearLayout(this)
                val startButton = button("START\n${formatTime(config.start)}") { pickTime(true) }
                val endButton = button("END\n${formatTime(config.end)}") { pickTime(false) }
                times.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                times.addView(endButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
                schedule.add(times, 2)
                dial.onChange = { start, end ->
                    startButton.text = "START\n${formatTime(start)}"
                    endButton.text = "END\n${formatTime(end)}"
                    persist { it.copy(start = start, end = end) }
                }
            }
        }
        if (config.mode != ScheduleMode.ALWAYS && !SleepScheduler.hasTimingPermission(this)) {
            schedule.add(button("Allow schedule access") { openTimingPermission() }, 8)
        }

        if (config.audio) {
            val sound = card("Sound")
            val active = ControlLive.audioState != null
            val gain = if (Prefs.isLevelInQuietZone(this)) (-Prefs.getAttenuation(this)).coerceIn(0f, 30f) else 0f
            quietIndicator = QuietLevelIndicator(this).apply { setLevel(gain, active) }
            sound.add(quietIndicator!!, 10)
            quietHint = text("", 12f, NightColors.secondary)
            sound.add(quietHint!!, 2)
            bluetoothCalibration(sound, active)
        }

        val screen = actionButton("Screen off", R.drawable.ic_screen_controls, config.screen, "Toggle screen controls") { toggleScreen() }
        val audio = actionButton("Audio off", R.drawable.ic_volume_slider, config.audio, "Toggle audio controls") { toggleAudio() }
        actions.addView(screen, LinearLayout.LayoutParams(0, dp(56), 1f))
        actions.addView(audio, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(8) })
        screenAction = screen; audioAction = audio
        refreshStatus(); scroll.post { scroll.scrollTo(0, y) }
    }

    private fun moreOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("How to use").setOnMenuItemClickListener { help(); true }
            menu.add("About & privacy").setOnMenuItemClickListener { about(); true }
            menu.add("Licenses").setOnMenuItemClickListener {
                startActivity(Intent(this@MainActivity, LicensesActivity::class.java)); true
            }
        }.show()
    }

    private fun help() {
        MaterialAlertDialogBuilder(this).setTitle("Quiet Night")
            .setMessage("Screen and Audio work independently.\n\nWarmth adds a tint; Dim darkens the display. Set either to 0% to turn it off. System brightness stays unchanged.\n\nSchedule applies to Screen only. Drag the dial or tap Start and End for exact times.\n\nWith Audio on, use your volume keys. Volume-down adds quiet steps below the usual minimum, then mutes after −30 dB. Volume-up brings sound back.\n\nFor notifications and Quick Settings, enable full-screen dimming. The accessibility service only draws a filter; it cannot read screens or control gestures.")
            .setNeutralButton("Full-screen dimming") { _, _ -> screenOverlayHelp() }
            .setPositiveButton("Done", null).show()
    }

    private fun screenOverlayHelp() {
        MaterialAlertDialogBuilder(this).setTitle("Full-screen dimming")
            .setMessage("Enable Quiet Night screen dimming in Accessibility settings to include notifications and Quick Settings.\n\nIt only draws a touch-through filter. It does not read screen content, observe actions, or control gestures. System brightness stays unchanged.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open settings") { _, _ ->
                timingPermission.launch(ScreenOverlayService.settingsIntent())
            }.show()
    }
    private fun refreshStatus() {
        val config = SleepSettings.read(this); val plan = SleepSchedule.plan(config)
        if (renderedSystemOverlay != ScreenOverlayService.isConnected() ||
            (config.audio && renderedAudioReady != (ControlLive.audioState != null))) { render(); return }
        styleAction(screenAction, "Screen", config.screen,
            SleepScheduler.screenReady(this, config) && SleepScheduler.timingReady(this, config), plan.active)
        styleAction(audioAction, "Audio", config.audio, true, ControlLive.audioState != null)
        if (config.audio && ControlLive.audioState == null) audioAction?.text = "Audio starting"
        val gain = if (Prefs.isLevelInQuietZone(this)) (-Prefs.getAttenuation(this)).coerceIn(0f, 30f) else 0f
        quietIndicator?.setLevel(gain, config.audio && ControlLive.audioState != null, ControlLive.audioState)
        val hint = when {
            !config.audio -> ""
            !ControlLive.running -> "Starting…"
            ControlLive.effect == ControlLive.Effect.NONE -> "Quiet steps unavailable on this device"
            StreamVolumeController(this).inCellularCall() -> "Unavailable during cellular calls"
            ControlLive.effect == ControlLive.Effect.FALLBACK -> "Compatibility mode · use volume keys"
            else -> "Use volume keys"
        }
        quietHint?.text = hint
        quietHint?.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun styleAction(action: ExtendedFloatingActionButton?, name: String, on: Boolean, ready: Boolean, active: Boolean) {
        action ?: return
        val live = on && ready && active
        action.text = when {
            !on -> "$name off"
            !ready -> "$name setup"
            active -> "$name on"
            else -> "$name scheduled"
        }
        action.contentDescription = "Turn $name controls ${if (on) "off" else "on"}"
        val fg = when { !on -> NightColors.text; live -> NightColors.background; else -> NightColors.lavender }
        action.setTextColor(fg)
        action.iconTint = ColorStateList.valueOf(fg)
        action.backgroundTintList = ColorStateList.valueOf(if (live) NightColors.amber else NightColors.surface)
    }
    private fun toggleScreen() {
        val on = !SleepSettings.read(this).screen
        persist(true) { it.copy(screen = on) }
        if (on) { requestScreenAccess(); requestNotifications() }
    }
    private fun toggleAudio() {
        val on = !SleepSettings.read(this).audio
        persist(true) { it.copy(audio = on) }
        if (on) {
            requestNotifications()
        }
    }
    private fun requestScreenAccess() {
        if (!ScreenOverlayService.isConnected() && !PermissionHelper.canDrawOverlays(this)) {
            overlayPermission.launch(PermissionHelper.overlayPermissionIntent(this)); return
        }
        continueScreenAccess()
    }
    private fun continueScreenAccess() {
        val config = SleepSettings.read(this)
        if (config.mode != ScheduleMode.ALWAYS && !SleepScheduler.hasTimingPermission(this)) openTimingPermission()
    }
    private fun requestNotifications() {
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
        if (!ControlLive.running || !SleepSettings.read(this).audio) { toast("Turn on Audio first"); return }
        startService(Intent(this, VolumeControlService::class.java).setAction(action).apply(extras))
    }
    private fun bluetoothCalibration(parent: LinearLayout, active: Boolean) {
        val volume = StreamVolumeController(this)
        if (!active || volume.inCall()) return
        val name = volume.activeBluetoothName() ?: return
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val min = maxOf(1, am.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)); val max = volume.calibrationMaxIndex()
        parent.add(button("Headphones · $name") {
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
            MaterialAlertDialogBuilder(this).setTitle("First audible step").setView(body).setPositiveButton("Done", null).show()
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
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Sun times location").setView(form)
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
        MaterialAlertDialogBuilder(this).setTitle("A quieter kind of evening")
            .setMessage("Quiet Night is a free, open-source fork of Granular Volume by Rzuss.\n\nEverything stays on your device. No ads, accounts, tracking, or internet permission. Location is optional and saved only to calculate sun times; there is no background location tracking.\n\nScreen and audio controls are independent. Evening light uses a touch-through display overlay, so it never changes your saved brightness or adaptive-brightness mode. Enable accessibility screen dimming to cover notifications and Quick Settings. This service only draws the filter; it does not read screen content or control gestures. Some protected screens and always-on displays may still hide filters.\n\nGPL-3.0. Source: github.com/JaredTweed/granular-volume")
            .setNeutralButton("Source code") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JaredTweed/granular-volume"))) }
                    .onFailure { toast("Source: github.com/JaredTweed/granular-volume") }
            }.setPositiveButton("Done", null).show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
