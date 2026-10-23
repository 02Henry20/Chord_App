package com.henry.chord

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.Assert.assertTrue

/** Tap navigation as soon as Android exposes it; no Compose wait-for-idle masks first-frame races. */
class ReleaseColdTapTest {
    @Test fun rapidFirstNavigationAfterRepeatedActivityCreation() {
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(10) {
            ActivityScenario.launch(MainActivity::class.java).use {
                val button=device.wait(Until.findObject(By.text("Library")),15000) ?: error("Library navigation did not appear")
                button.click()
                assertTrue("Initial navigation $it",device.wait(Until.hasObject(By.text("Your library")),15000))
            }
        }
    }
}
