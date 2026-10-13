package com.granularvolume.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The web pages the app can hand to the browser. The app itself never loads them: it has no
 * network permission, and each of these is an ACTION_VIEW that another app answers.
 */
object Links {
    /** 1.7.0: setup, a control that stops by itself, restoring a purchase, device limits. */
    const val HELP = "https://granularvolume.com/help.html"

    /** 1.7.2: the help section for someone who cannot find the app in Android's permission list. */
    const val HELP_FIND = "https://granularvolume.com/help.html#find"

    fun open(context: Context, url: String) {
        // Android's own log shortens the address of a VIEW request on older versions, so the
        // app says which page it asked for. These are fixed public addresses, nothing personal.
        android.util.Log.i("GranularVolume", "open link: $url")
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
