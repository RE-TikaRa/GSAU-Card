package com.tika.paycard.data

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserConsentTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("user_consent", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `首次启动未同意协议`() {
        assertFalse(UserConsent.hasAccepted(context))
    }

    @Test
    fun `同意当前协议后通过且新版本需要重新确认`() {
        UserConsent.accept(context)

        assertTrue(UserConsent.hasAccepted(context))
        assertTrue(UserConsent.hasAcceptedVersion(context, UserConsent.CURRENT_VERSION))
        assertFalse(UserConsent.hasAcceptedVersion(context, UserConsent.CURRENT_VERSION + 1))
    }
}
