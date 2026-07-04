package com.tika.paycard.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
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

    private fun showCached() {
        val account = AccountStore.get(this).current()
        if (account == null) {
            binding.payName.text = "未添加账号"
            binding.payHint.text = "请先在主界面添加校园卡"
            return
        }
        binding.payName.text = account.displayName()
        binding.payBalance.text = if (account.balance.isNotBlank()) "余额 ${account.balance}" else ""
        if (account.cachedCode.isNotBlank()) {
            binding.payQr.setImageBitmap(QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_FULLSCREEN))
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
        binding.payHint.text = "刷新中…"
        lifecycleScope.launch {
            val r = PayCodeManager.refresh(this@PayActivity, account)
            // 刷新期间当前账号可能已被切换,只更新仍是当前账号的结果,避免展示错卡
            if (AccountStore.get(this@PayActivity).current()?.openid != account.openid) return@launch
            when (r) {
                is PayCodeRepository.Result.Ok -> {
                    binding.payQr.setImageBitmap(QrGenerator.encode(r.code, QrGenerator.SIZE_FULLSCREEN))
                    binding.payName.text = account.displayName()
                    binding.payBalance.text = if (r.balance.isNotBlank()) "余额 ${r.balance}" else ""
                    binding.payHint.text = "每分钟自动刷新，点二维码可手动刷新"
                    PayWidgetProvider.refreshAll(this@PayActivity)
                }
                is PayCodeRepository.Result.Invalid -> {
                    binding.payHint.text = "凭证失效，请重新粘贴链接添加"
                }
                is PayCodeRepository.Result.Error -> {
                    binding.payHint.text = "刷新失败：${r.message}"
                }
            }
        }
    }
}
