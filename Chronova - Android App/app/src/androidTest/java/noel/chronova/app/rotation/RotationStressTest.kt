package noel.chronova.app.rotation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import noel.chronova.app.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turns the real app between portrait and landscape many times and checks it is still running with
 * exactly one live activity (no crash, no leaked or duplicated activity).
 *
 * It runs against the phone's real app and data: run it through `tools/qa/rotation-stress.sh
 * instrumented`, which backs the data up and restores it, and never with connectedAndroidTest
 * (which uninstalls the app and its data).
 */
@RunWith(AndroidJUnit4::class)
class RotationStressTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pkg = "noel.chronova.app"

    @After
    fun backToPortrait() {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test
    fun turningThePhone50TimesKeepsOneLiveActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15_000)

        repeat(50) {
            device.setOrientationLeft()
            device.setOrientationNatural()
        }
        device.waitForIdle()

        assertEquals(pkg, device.currentPackageName)
        var resumed = -1
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
        }
        assertEquals("live activities after 50 rotations", 1, resumed)
    }
}
