package com.granularvolume.sleep

import org.junit.Assert.*
import org.junit.Test

/** Pure transaction tests: no real display settings are touched. */
class ScreenDimmingTest {
    private class Access : BrightnessAccess {
        var allowed = true
        var level: Int? = 200
        var automatic: Int? = 1
        var rejectBrightness = false
        var rejectMode = false
        val changes = mutableListOf<String>()
        override fun permitted() = allowed
        override fun brightness() = level
        override fun mode() = automatic
        override fun setBrightness(value: Int): Boolean {
            if (!allowed || rejectBrightness) return false
            level = value; changes.add("brightness=$value"); return true
        }
        override fun setMode(value: Int): Boolean {
            if (!allowed || rejectMode) return false
            automatic = value; changes.add("mode=$value"); return true
        }
    }
    private class Store : BrightnessStorage {
        var data: BrightnessRecord? = null
        var userControl = false
        var writable = true
        override fun record() = data
        override fun yielded() = userControl
        override fun save(record: BrightnessRecord?, yielded: Boolean): Boolean {
            if (!writable) return false
            data = record; userControl = yielded; return true
        }
    }
    private val access = Access()
    private val store = Store()
    private val session = BrightnessSession(access, store)
    @Test fun dimsGloballyAndRestoresAdaptiveMode() {
        assertEquals(ScreenDimming.Mode.SYSTEM, session.apply(80).mode)
        assertEquals(40, access.level); assertEquals(0, access.automatic)
        assertEquals(0, session.apply(80).overlayDim)
        assertTrue(session.restore())
        assertEquals(200, access.level); assertEquals(1, access.automatic); assertNull(store.data)
    }
    @Test fun repeatedUpdatesNeverCompound() {
        session.apply(80); session.apply(50); session.apply(25)
        assertEquals(150, access.level)
        session.restore(); assertEquals(200, access.level)
    }
    @Test fun identicalUpdatesDoNotWriteAgain() {
        session.apply(50); access.changes.clear(); session.apply(50)
        assertTrue(access.changes.isEmpty())
    }
    @Test fun processRestartKeepsOriginalBaseline() {
        session.apply(75)
        val restarted = BrightnessSession(access, store)
        restarted.apply(75); assertEquals(50, access.level)
        restarted.restore(); assertEquals(200, access.level); assertEquals(1, access.automatic)
    }
    @Test fun permissionDeniedUsesOverlayWithoutChangingSettings() {
        access.allowed = false
        assertEquals(ScreenDimming.Result(ScreenDimming.Mode.APP_ONLY, 85), session.apply(85))
        assertNull(store.data); assertTrue(access.changes.isEmpty())
    }
    @Test fun zeroDimRestoresWithoutDoubleDimming() {
        session.apply(85)
        assertEquals(ScreenDimming.Result(ScreenDimming.Mode.OFF, 0), session.apply(0))
        assertEquals(200, access.level); assertEquals(1, access.automatic)
    }
    @Test fun quickSettingsChangeWinsUntilExplicitRestart() {
        session.apply(50); access.level = 130
        assertTrue(session.checkForUserChange())
        assertEquals(ScreenDimming.Mode.USER_CONTROL, session.apply(85).mode)
        assertEquals(130, access.level)
        session.restore(); assertEquals(130, access.level); assertEquals(0, access.automatic)
        session.apply(50); assertEquals(65, access.level)
    }
    @Test fun userEnablingAdaptiveBrightnessIsNotUndone() {
        session.apply(50); access.automatic = 1
        assertEquals(ScreenDimming.Mode.USER_CONTROL, session.apply(50).mode)
        session.restore(); assertEquals(1, access.automatic); assertEquals(100, access.level)
    }
    @Test fun failedWriteRollsBackPartialModeChange() {
        access.rejectBrightness = true
        assertEquals(ScreenDimming.Mode.APP_ONLY, session.apply(50).mode)
        assertEquals(200, access.level); assertEquals(1, access.automatic); assertNull(store.data)
    }
    @Test fun failedSliderUpdateKeepsRecoverySnapshot() {
        session.apply(50); access.rejectBrightness = true
        assertEquals(ScreenDimming.Mode.RESTORE_NEEDED, session.apply(80).mode)
        assertNotNull(store.data)
        access.rejectBrightness = false
        assertTrue(session.restore()); assertEquals(200, access.level); assertEquals(1, access.automatic)
    }
    @Test fun interruptedApplyIsRecoverable() {
        store.data = BrightnessRecord(200, 1, 40, "applying", 100)
        access.level = 100; access.automatic = 0
        assertTrue(session.restore()); assertEquals(200, access.level); assertEquals(1, access.automatic)
    }
    @Test fun interruptedRestoreRetainsSnapshot() {
        session.apply(50); access.rejectMode = true
        assertFalse(session.restore()); assertNotNull(store.data); assertEquals(200, access.level)
        access.rejectMode = false
        assertTrue(BrightnessSession(access, store).restore()); assertEquals(1, access.automatic)
    }
    @Test fun revokedPermissionKeepsRecoveryDataWithoutDoubleDimming() {
        session.apply(50); access.allowed = false
        assertFalse(session.restore()); assertNotNull(store.data)
        assertEquals(0, session.apply(85).overlayDim)
        access.allowed = true
        assertTrue(session.restore()); assertEquals(200, access.level)
    }
    @Test fun providerFailureRetainsRecoveryData() {
        session.apply(50); access.level = null
        assertFalse(session.restore()); assertNotNull(store.data)
        assertEquals(ScreenDimming.Mode.RESTORE_NEEDED, session.apply(50).mode)
        access.level = 100
        assertTrue(session.restore()); assertEquals(200, access.level)
    }
    @Test fun noGlobalWriteWithoutDurableSnapshot() {
        store.writable = false
        assertEquals(ScreenDimming.Mode.APP_ONLY, session.apply(50).mode)
        assertTrue(access.changes.isEmpty())
    }
    @Test fun clampDimAndRespectMinimumBrightness() {
        session.apply(100); assertEquals(30, access.level)
        session.restore(); access.level = 0
        session.apply(85); assertEquals(0, access.level)
    }
}
