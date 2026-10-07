package com.tika.paycard.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.tika.paycard.R
import com.tika.paycard.data.Account
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.PayCodePolicy
import com.tika.paycard.ui.PayActivity
import com.tika.paycard.work.WidgetExpiry
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayWidgetProviderTest {

    private lateinit var context: Context
    private lateinit var account: Account

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val store = AccountStore.get(context)
        store.save(emptyList())
        store.setCurrentIndex(0)
        account = Account(
            openid = "first",
            cardId = "9",
            cachedCode = "0a1b2c3d4e5f60718293a4b5c6d7e8f9",
            cachedAt = System.currentTimeMillis()
        )
        store.add(account)
    }

    @Test
    fun `无精确闹钟权限时隐藏付款码并进入付款页`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val id = widgets.createWidget(PayWidgetProvider::class.java, R.layout.widget_paycard)
        val view = widgets.getViewFor(id)

        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_qr).visibility)
        assertEquals(context.getString(R.string.widget_open_pay), view.findViewById<TextView>(R.id.widget_hint).text)
        view.findViewById<View>(R.id.widget_refresh).performClick()
        assertEquals(PayActivity::class.java.name, shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.component?.className)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    @Test
    fun `有权限时显示付款码并在到期后隐藏`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ShadowAlarmManager.setAutoSchedule(true)
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val id = widgets.createWidget(PayWidgetProvider::class.java, R.layout.widget_paycard)

        assertEquals(View.VISIBLE, widgets.getViewFor(id).findViewById<View>(R.id.widget_qr).visibility)
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals(0L, alarm.windowLengthMs)
        assertTrue(alarm.isAllowWhileIdle)

        AccountStore.get(context).update(account.copy(cachedAt = System.currentTimeMillis() - PayCodePolicy.VALIDITY_MS))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PayCodePolicy.VALIDITY_MS))

        assertEquals(View.GONE, widgets.getViewFor(id).findViewById<View>(R.id.widget_qr).visibility)
        assertEquals(context.getString(R.string.widget_tap_refresh), widgets.getViewFor(id).findViewById<TextView>(R.id.widget_hint).text)
    }

    @Test
    fun `授予权限后重绘已有组件并安排撤码`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val id = widgets.createWidget(PayWidgetProvider::class.java, R.layout.widget_paycard)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        PayWidgetProvider().onReceive(context, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))

        assertEquals(View.VISIBLE, widgets.getViewFor(id).findViewById<View>(R.id.widget_qr).visibility)
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }

    @Test
    fun `刷新付款码后撤码时间使用最新有效期`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ShadowAlarmManager.setAutoSchedule(true)
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val id = widgets.createWidget(PayWidgetProvider::class.java, R.layout.widget_paycard)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        AccountStore.get(context).update(account.copy(cachedAt = System.currentTimeMillis()))
        PayWidgetProvider().onReceive(context, Intent(WidgetExpiry.ACTION))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))

        assertEquals(View.VISIBLE, widgets.getViewFor(id).findViewById<View>(R.id.widget_qr).visibility)

        AccountStore.get(context).update(account.copy(cachedAt = System.currentTimeMillis() - PayCodePolicy.VALIDITY_MS))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))

        assertEquals(View.GONE, widgets.getViewFor(id).findViewById<View>(R.id.widget_qr).visibility)
    }
}
