package com.tika.paycard.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.tika.paycard.R
import com.tika.paycard.data.Account
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.LinkParser
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodeRepository
import com.tika.paycard.databinding.ActivityMainBinding
import com.tika.paycard.qr.QrGenerator
import com.tika.paycard.widget.PayWidgetProvider
import com.tika.paycard.work.KeepAlive
import kotlinx.coroutines.launch

/**
 * 主界面:展示当前卡(姓名条+大二维码),账号切换与管理,粘贴链接添加,设置入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: AccountStore
    private lateinit var adapter: AccountAdapter
    private lateinit var appliedScheme: ColorManager.Scheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorManager.applyOverlay(this)
        appliedScheme = ColorManager.getScheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = AccountStore.get(this)

        maybeShowGuide()

        adapter = AccountAdapter(
            onClick = { index -> selectAccount(index) },
            onLongClick = { index -> confirmRemove(index) }
        )
        binding.accountList.layoutManager = LinearLayoutManager(this)
        binding.accountList.adapter = adapter

        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardQr.setOnClickListener {
            startActivity(Intent(this, PayActivity::class.java))
        }

        // 首次启动按当前档位拉起保活
        KeepAlive.apply(this)
    }

    private fun maybeShowGuide() {
        val prefs = getSharedPreferences(PREFS_GUIDE, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_GUIDE_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
        startActivity(Intent(this, GuideActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (ColorManager.getScheme(this) != appliedScheme) {
            recreate()
            return
        }
        renderCurrent()
        adapter.submit(store.list(), store.currentIndex())
    }

    private fun showBalance(balance: String) {
        if (balance.isNotBlank()) {
            binding.cardBalance.text = getString(R.string.balance_format, balance)
            binding.cardBalance.visibility = View.VISIBLE
        } else {
            binding.cardBalance.visibility = View.GONE
        }
    }

    private fun renderCurrent() {
        clearHintAction()
        val account = store.current()
        if (account == null) {
            binding.cardName.text = getString(R.string.main_no_card)
            binding.cardBalance.visibility = View.GONE
            binding.cardQr.setImageDrawable(null)
            binding.cardHint.text = getString(R.string.main_add_hint)
            return
        }
        binding.cardName.text = account.displayName()
        showBalance(account.balance)
        if (account.cachedCode.isNotBlank()) {
            binding.cardQr.setImageBitmap(
                QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_CARD)
            )
            binding.cardHint.text = getString(R.string.main_tap_qr)
        } else {
            binding.cardHint.text = getString(R.string.main_loading)
        }
        refreshCurrent(account)
    }

    private fun refreshCurrent(account: Account) {
        lifecycleScope.launch {
            val r = PayCodeManager.refresh(this@MainActivity, account)
            // 刷新期间可能已切换账号,只更新仍是当前账号的结果
            if (store.current()?.openid != account.openid) return@launch
            when (r) {
                is PayCodeRepository.Result.Ok -> {
                    clearHintAction()
                    binding.cardName.text = account.displayName()
                    showBalance(r.balance)
                    binding.cardQr.setImageBitmap(
                        QrGenerator.encode(r.code, QrGenerator.SIZE_CARD)
                    )
                    binding.cardHint.text = getString(R.string.main_tap_qr)
                    adapter.submit(store.list(), store.currentIndex())
                    PayWidgetProvider.refreshAll(this@MainActivity)
                }
                is PayCodeRepository.Result.Invalid -> showInvalidHint(account)
                is PayCodeRepository.Result.Error ->
                    binding.cardHint.text = getString(R.string.main_fetch_failed, r.message)
            }
        }
    }

    /** 清掉提示的可点态,恢复成普通说明文字。 */
    private fun clearHintAction() {
        binding.cardHint.setOnClickListener(null)
        binding.cardHint.isClickable = false
        binding.cardHint.setTextColor(
            ContextCompat.getColor(this, R.color.text_hint)
        )
    }

    /** 凭证失效:提示改成可点,点击进入重新绑定。 */
    private fun showInvalidHint(account: Account) {
        binding.cardHint.text = getString(R.string.main_invalid)
        binding.cardHint.setTextColor(
            MaterialColors.getColor(binding.cardHint, com.google.android.material.R.attr.colorPrimary)
        )
        binding.cardHint.isClickable = true
        binding.cardHint.setOnClickListener { showRebindDialog(account) }
    }

    /** 用新链接就地替换当前失效卡,openid 换新,cardId 沿用原卡。 */
    private fun showRebindDialog(invalid: Account) {
        val index = store.list().indexOfFirst {
            it.openid == invalid.openid && it.cardId == invalid.cardId
        }
        if (index < 0) return
        AppDialog.input(
            context = this,
            title = getString(R.string.rebind_dialog_title, invalid.displayName()),
            message = getString(R.string.rebind_dialog_message),
            hint = getString(R.string.add_dialog_hint),
            positiveText = getString(R.string.rebind_action),
            onPositive = { link -> rebind(index, link) }
        )
    }

    private fun rebind(index: Int, link: String) {
        val parsed = LinkParser.parse(link)
        if (parsed == null) {
            AppDialog.notice(binding.root, getString(R.string.add_no_openid))
            return
        }
        val account = Account(openid = parsed.openid, cardId = parsed.cardId)
        lifecycleScope.launch {
            when (val r = PayCodeManager.refresh(this@MainActivity, account)) {
                is PayCodeRepository.Result.Ok -> {
                    store.replaceAt(index, account)
                    store.setCurrentIndex(index)
                    renderCurrent()
                    adapter.submit(store.list(), store.currentIndex())
                    PayWidgetProvider.refreshAll(this@MainActivity)
                    AppDialog.notice(binding.root, getString(R.string.rebind_success, account.displayName()))
                }
                is PayCodeRepository.Result.Invalid ->
                    AppDialog.notice(binding.root, getString(R.string.rebind_still_invalid))
                is PayCodeRepository.Result.Error ->
                    AppDialog.notice(binding.root, getString(R.string.add_verify_failed, r.message))
            }
        }
    }

    private fun selectAccount(index: Int) {
        store.setCurrentIndex(index)
        renderCurrent()
        adapter.submit(store.list(), store.currentIndex())
        PayWidgetProvider.refreshAll(this)
    }

    private fun showAddDialog() {
        AppDialog.input(
            context = this,
            title = getString(R.string.add_dialog_title),
            message = getString(R.string.add_dialog_message),
            hint = getString(R.string.add_dialog_hint),
            positiveText = getString(R.string.add_action),
            onPositive = { link -> addFromLink(link) }
        )
    }

    private fun addFromLink(link: String) {
        val parsed = LinkParser.parse(link)
        if (parsed == null) {
            AppDialog.notice(binding.root, getString(R.string.add_no_openid))
            return
        }
        val account = Account(openid = parsed.openid, cardId = parsed.cardId)
        lifecycleScope.launch {
            when (val r = PayCodeManager.refresh(this@MainActivity, account)) {
                is PayCodeRepository.Result.Ok -> {
                    store.add(account)
                    val newIndex = store.list().indexOfFirst {
                        it.openid == account.openid && it.cardId == account.cardId
                    }
                    if (newIndex >= 0) store.setCurrentIndex(newIndex)
                    renderCurrent()
                    adapter.submit(store.list(), store.currentIndex())
                    PayWidgetProvider.refreshAll(this@MainActivity)
                    AppDialog.notice(binding.root, getString(R.string.add_success, account.displayName()))
                }
                is PayCodeRepository.Result.Invalid ->
                    AppDialog.notice(binding.root, getString(R.string.add_invalid))
                is PayCodeRepository.Result.Error ->
                    AppDialog.notice(binding.root, getString(R.string.add_verify_failed, r.message))
            }
        }
    }

    private fun confirmRemove(index: Int) {
        val account = store.list().getOrNull(index) ?: return
        AppDialog.confirm(
            context = this,
            title = getString(R.string.remove_dialog_title),
            message = getString(R.string.remove_dialog_message, account.displayName()),
            positiveText = getString(R.string.remove_action),
            onPositive = {
                store.removeAt(index)
                renderCurrent()
                adapter.submit(store.list(), store.currentIndex())
                PayWidgetProvider.refreshAll(this)
            }
        )
    }

    companion object {
        private const val PREFS_GUIDE = "paycard_guide"
        private const val KEY_GUIDE_SHOWN = "guide_shown"
    }
}
