package com.tika.paycard.work

import android.content.Context
import android.content.Intent
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.tika.paycard.data.Account
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.UserConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshWorkerTest {

    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private lateinit var store: AccountStore

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        UserConsent.accept(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        manager = WorkManager.getInstance(context)
        store = AccountStore.get(context)
        store.save(emptyList())
    }

    @Test
    fun `协议未确认时不安排周期任务`() {
        store.add(Account("first", "9"))
        context.getSharedPreferences("user_consent", Context.MODE_PRIVATE).edit().clear().commit()

        RefreshWorker.schedule(context)

        assertTrue(work().isEmpty())
    }

    @Test
    fun `无卡启动或开机不创建周期任务`() {
        KeepAlive.enterForeground(context)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(work().isEmpty())
    }

    @Test
    fun `删除最后一张卡取消任务且再次添加可以恢复`() {
        store.add(Account("first", "9"))
        RefreshWorker.schedule(context)
        val first = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        store.removeAt(0)
        KeepAlive.enterForeground(context)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(first.id).get().state)

        store.add(Account("second", "9"))
        KeepAlive.enterForeground(context)
        assertEquals(1, work().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `重复调度保留同一个周期任务`() {
        store.add(Account("first", "9"))
        RefreshWorker.schedule(context)
        val id = work().single().id

        KeepAlive.enterForeground(context)
        KeepAlive.apply(context, KeepAlive.Mode.LITE)

        assertEquals(id, work().single().id)
    }

    private fun work() = manager.getWorkInfosForUniqueWork("paycode_refresh").get()
}
