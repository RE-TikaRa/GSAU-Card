package com.tika.paycard.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tika.paycard.R
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodeRepository
import com.tika.paycard.databinding.ActivityPayBinding
import com.tika.paycard.qr.QrGenerator
import com.tika.paycard.widget.PayWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全屏付款页:打开即实时拉一次最新码,之后每 60 秒自动刷新。
 * 这条路不依赖后台,系统再狠也掐不掉,保证付款那一刻码是新的。
 */
class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private var loopJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        binding.payQr.setOnClickListener { refresh() }
        binding.payRefresh.setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        showCached()
        startLoop()
    }

    override fun onPause() {
        super.onPause()
        loopJob?.cancel()
    }

    private fun qrBackground() = ContextCompat.getColor(this, R.color.qr_background)

    private fun showCached() {
        val account = AccountStore.get(this).current()
        if (account == null) {
            binding.payName.text = getString(R.string.pay_no_card)
            binding.payHint.text = getString(R.string.pay_no_card_hint)
            return
        }
        binding.payName.text = account.displayName()
        binding.payBalance.text =
            if (account.balance.isNotBlank()) getString(R.string.balance_format, account.balance) else ""
        if (account.cachedCode.isNotBlank()) {
            binding.payQr.setImageBitmap(
                QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_FULLSCREEN, background = qrBackground())
            )
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = lifecycleScope.launch {
            while (isActive) {
                refresh()
                delay(60_000L)
            }
        }
    }

    private fun refresh() {
        val account = AccountStore.get(this).current() ?: return
        binding.payHint.text = getString(R.string.pay_refreshing)
        lifecycleScope.launch {
            val r = PayCodeManager.refresh(this@PayActivity, account)
            // 刷新期间当前账号可能已被切换,只更新仍是当前账号的结果,避免展示错卡
            if (AccountStore.get(this@PayActivity).current()?.openid != account.openid) return@launch
            when (r) {
                is PayCodeRepository.Result.Ok -> {
                    binding.payQr.setImageBitmap(
                        QrGenerator.encode(r.code, QrGenerator.SIZE_FULLSCREEN, background = qrBackground())
                    )
                    binding.payName.text = account.displayName()
                    binding.payBalance.text =
                        if (r.balance.isNotBlank()) getString(R.string.balance_format, r.balance) else ""
                    binding.payHint.text = getString(R.string.pay_auto_refresh)
                    PayWidgetProvider.refreshAll(this@PayActivity)
                }
                is PayCodeRepository.Result.Invalid -> {
                    binding.payHint.text = getString(R.string.pay_invalid)
                }
                is PayCodeRepository.Result.Error -> {
                    binding.payHint.text = getString(R.string.pay_refresh_failed, r.message)
                }
            }
        }
    }
}
