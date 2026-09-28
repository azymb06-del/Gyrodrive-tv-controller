package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Gyro Wheel", appName)
    }

    @Test
    fun `activity launches and initializes main menu and cockpit controls`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                assertNotNull(activity.findViewById(R.id.btnLaunchCockpit))
                assertNotNull(activity.findViewById(R.id.btnMenuScan))
                assertNotNull(activity.findViewById(R.id.groupAxis))
                assertNotNull(activity.findViewById(R.id.btnAxisZ))
                assertNotNull(activity.findViewById(R.id.switchInvertAxis))
                assertNotNull(activity.findViewById(R.id.groupProfiles))
                assertNotNull(activity.findViewById(R.id.switchVibration))
                assertNotNull(activity.findViewById(R.id.btnCockpitBrake))
                assertNotNull(activity.findViewById(R.id.btnCockpitThrottle))
            }
        }
    }
}
