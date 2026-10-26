package dev.jaredtweed.quietnight

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Old notification/deep-link entry points now lead to the permanent home screen. */
class InfoSheetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java)); finish()
    }
}
