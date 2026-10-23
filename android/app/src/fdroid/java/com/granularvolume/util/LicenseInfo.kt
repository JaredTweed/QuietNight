package com.granularvolume.util

/** Runtime components included in the entirely open-source Quiet Night build. */
object LicenseInfo {

    /** name to licence, shown in order. */
    val openSource: List<Pair<String, String>> = listOf(
        "AndroidX Core KTX" to "The Android Open Source Project, Apache License 2.0",
        "AndroidX AppCompat" to "The Android Open Source Project, Apache License 2.0",
        "AndroidX Lifecycle Service" to "The Android Open Source Project, Apache License 2.0",
        "Material Components for Android" to "Google LLC, Apache License 2.0",
        "Kotlin Coroutines for Android" to "JetBrains s.r.o., Apache License 2.0",
        "Kotlin Standard Library" to "JetBrains s.r.o. and Kotlin Programming Language contributors, Apache License 2.0",
    )

    /** Proprietary components. Empty here, and that is the whole difference between the builds. */
    val proprietary: List<Pair<String, String>> = emptyList()
}
