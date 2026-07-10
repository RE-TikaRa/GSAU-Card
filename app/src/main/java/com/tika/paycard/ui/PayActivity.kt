package com.tika.paycard.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tika.paycard.R
import com.tika.paycard.data.Account
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodePolicy
import com.tika.paycard.data.PayCodeRepository
import com.tika.paycard.databinding.ActivityPayBinding
import com.tika.paycard.qr.QrGenerator
import com.tika.paycard.widget.PayWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全屏付款页:打开即实时拉一次最新码,之后每 30 秒自动刷新。
 * 这条路不依赖后台,系统再狠也掐不掉,保证付款那一刻码是新的。
 */
class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private var loopJob: Job? = null
    private var expiryJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorManager.applyOverlay(this)
        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 付款时提亮屏幕,方便扫码
        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.topbar.topbarTitle.text = getString(R.string.pay_title)
        // 返回按钮回软件主页面:从桌面组件单独起时栈里没有主界面,新建;App 内进来则复用已有实例。
        binding.topbar.btnBack.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        binding.payQr.setOnClickListener { lifecycleScope.launch { refresh() } }
        binding.payRefresh.setOnClickListener { lifecycleScope.launch { refresh() } }
    }

    override fun onResume() {
        super.onResume()
        showCached()
        startLoop()
    }

    override fun onPause() {
        loopJob?.cancel()
        expiryJob?.cancel()
        super.onPause()
    }

    private fun showCached() {
        val account = AccountStore.get(this).current()
        if (account == null) {
            expiryJob?.cancel()
            binding.payQr.setImageDrawable(null)
            binding.payName.text = getString(R.string.pay_no_card)
            binding.payHint.text = getString(R.string.pay_no_card_hint)
            return
        }
        binding.payName.text = account.displayName()
        binding.payBalance.text =
            if (account.balance.isNotBlank()) getString(R.string.balance_format, account.balance) else ""
        if (account.hasFreshCode()) {
            binding.payQr.setImageBitmap(
                QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_FULLSCREEN)
            )
            scheduleExpiry(account)
        } else {
            expiryJob?.cancel()
            binding.payQr.setImageDrawable(null)
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = lifecycleScope.launch {
            while (isActive) {
                refresh()
                delay(PayCodePolicy.REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        val account = AccountStore.get(this).current() ?: return
        binding.payHint.text = getString(R.string.pay_refreshing)
        val r = PayCodeManager.refresh(this@PayActivity, account)
        // 刷新期间当前账号可能已被切换,丢弃本次结果并回到当前账号的稳定展示,不把提示卡在"刷新中"
        if (!account.sameCard(AccountStore.get(this@PayActivity).current())) {
            showCached()
            binding.payHint.text = getString(R.string.pay_auto_refresh)
            return
        }
        when (r) {
            is PayCodeRepository.Result.Ok -> {
                binding.payQr.setImageBitmap(
                    QrGenerator.encode(r.code, QrGenerator.SIZE_FULLSCREEN)
                )
                scheduleExpiry(account)
                binding.payName.text = account.displayName()
                binding.payBalance.text =
                    if (r.balance.isNotBlank()) getString(R.string.balance_format, r.balance) else ""
                binding.payHint.text = getString(R.string.pay_auto_refresh)
                PayWidgetProvider.refreshAll(this@PayActivity)
            }
            is PayCodeRepository.Result.Invalid -> {
                showCached()
                binding.payHint.text = getString(R.string.pay_invalid)
            }
            is PayCodeRepository.Result.Error -> {
                showCached()
                binding.payHint.text = getString(R.string.pay_refresh_failed, r.message)
            }
        }
    }

    private fun scheduleExpiry(account: Account) {
        expiryJob?.cancel()
        val remaining = account.cachedAt + PayCodePolicy.VALIDITY_MS - System.currentTimeMillis()
        expiryJob = lifecycleScope.launch {
            delay(remaining.coerceAtLeast(0L))
            expiryJob = null
            val current = AccountStore.get(this@PayActivity).current() ?: return@launch
            if (!account.sameCard(current)) return@launch
            if (current.hasFreshCode()) showCached() else binding.payQr.setImageDrawable(null)
        }
    }
}
