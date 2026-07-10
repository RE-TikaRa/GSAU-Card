package com.tika.paycard.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.tika.paycard.R
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodePolicy
import com.tika.paycard.widget.PayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 稳妥档保活:常驻前台服务,内部每 30 秒刷新一次当前账号并更新组件。
 * 用低优先级通知,尽量不打扰。用户可在设置里关闭。
 */
class RefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loopJob?.isActive != true) {
            loopJob = scope.launch {
                while (isActive) {
                    val startedAt = SystemClock.elapsedRealtime()
                    runCatching { PayCodeManager.refreshCurrent(applicationContext) }
                    PayWidgetProvider.refreshAll(applicationContext)
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    delay((PayCodePolicy.REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(0L))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "paycode_keepalive"
        private const val NOTIF_ID = 1001
        fun start(context: Context) {
            val intent = Intent(context, RefreshService::class.java)
            // Android 12+ 后台启前台服务会抛 ForegroundServiceStartNotAllowedException,兜住等下次前台时机再起
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RefreshService::class.java))
        }
    }
}
