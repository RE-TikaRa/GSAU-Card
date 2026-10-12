package com.tika.paycard.ui

import android.content.Context
import android.widget.Button
import android.widget.CompoundButton
import com.tika.paycard.R
import com.tika.paycard.data.UserConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgreementActivityTest {

    @Before
    fun setup() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("user_consent", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `未勾选时不能继续`() {
        val activity = Robolectric.buildActivity(AgreementActivity::class.java).setup().get()
        val accept = activity.findViewById<Button>(R.id.agreementAccept)

        assertFalse(accept.isEnabled)
        accept.performClick()
        assertTrue(activity.isFinishing.not())
        assertFalse(UserConsent.hasAccepted(activity))
    }

    @Test
    fun `应用桌面入口指向协议确认页`() {
        val intent = RuntimeEnvironment.getApplication().packageManager
            .getLaunchIntentForPackage(RuntimeEnvironment.getApplication().packageName)

        assertEquals(AgreementActivity::class.java.name, intent?.component?.className)
    }

    @Test
    fun `勾选并同意后进入主界面并记录当前协议版本`() {
        val activity = Robolectric.buildActivity(AgreementActivity::class.java).setup().get()
        activity.findViewById<CompoundButton>(R.id.agreementCheck).performClick()
        val accept = activity.findViewById<Button>(R.id.agreementAccept)

        assertTrue(accept.isEnabled)
        accept.performClick()

        assertTrue(UserConsent.hasAccepted(activity))
        assertEquals(MainActivity::class.java.name,
            org.robolectric.Shadows.shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun `拒绝协议结束启动流程`() {
        val activity = Robolectric.buildActivity(AgreementActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.agreementDecline).performClick()

        assertTrue(activity.isFinishing)
        assertFalse(UserConsent.hasAccepted(activity))
    }
}
