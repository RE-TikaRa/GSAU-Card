package com.tika.paycard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = AccountStore.get(this)

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

    override fun onResume() {
        super.onResume()
        renderCurrent()
        adapter.submit(store.list(), store.currentIndex())
    }

    private fun renderCurrent() {
        val account = store.current()
        if (account == null) {
            binding.cardName.text = getString(R.string.main_no_card)
            binding.cardBalance.text = ""
            binding.cardQr.setImageDrawable(null)
            binding.cardHint.text = getString(R.string.main_add_hint)
            return
        }
        binding.cardName.text = account.displayName()
        binding.cardBalance.text =
            if (account.balance.isNotBlank()) getString(R.string.balance_format, account.balance) else ""
        if (account.cachedCode.isNotBlank()) {
            binding.cardQr.setImageBitmap(QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_CARD))
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
                    binding.cardName.text = account.displayName()
                    binding.cardBalance.text =
                        if (r.balance.isNotBlank()) getString(R.string.balance_format, r.balance) else ""
                    binding.cardQr.setImageBitmap(QrGenerator.encode(r.code, QrGenerator.SIZE_CARD))
                    binding.cardHint.text = getString(R.string.main_tap_qr)
                    adapter.submit(store.list(), store.currentIndex())
                    PayWidgetProvider.refreshAll(this@MainActivity)
                }
                is PayCodeRepository.Result.Invalid ->
                    binding.cardHint.text = getString(R.string.main_invalid)
                is PayCodeRepository.Result.Error ->
                    binding.cardHint.text = getString(R.string.main_fetch_failed, r.message)
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
            Toast.makeText(this, R.string.add_no_openid, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.add_success, account.displayName()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is PayCodeRepository.Result.Invalid ->
                    Toast.makeText(this@MainActivity, R.string.add_invalid, Toast.LENGTH_LONG).show()
                is PayCodeRepository.Result.Error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.add_verify_failed, r.message),
                        Toast.LENGTH_LONG
                    ).show()
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
}
