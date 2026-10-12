package com.tika.paycard.work

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.tika.paycard.data.Account
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.UserConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshServiceTest {

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        UserConsent.accept(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        AccountStore.get(context).add(Account("first", "9"))
    }

    @Test
    fun `系统拒绝服务启动时返回失败并保留所选模式`() {
        val blocked = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startForegroundService(service: Intent): ComponentName {
                throw IllegalStateException("Background start not allowed")
            }
        }

        assertFalse(KeepAlive.setMode(blocked, KeepAlive.Mode.STEADY))
        assertEquals(KeepAlive.Mode.STEADY, KeepAlive.getMode(blocked))
        val log = ShadowLog.getLogsForTag("RefreshService").last()
        assertEquals("Background start not allowed", log.throwable.message)
    }

    @Test
    fun `没有卡片时不启动前台服务`() {
        AccountStore.get(RuntimeEnvironment.getApplication()).save(emptyList())
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startForegroundService(service: Intent): ComponentName {
                error("No service expected")
            }
        }

        assertFalse(KeepAlive.apply(context, KeepAlive.Mode.STEADY))
        assertTrue(KeepAlive.apply(context, KeepAlive.Mode.LITE))
    }
}
