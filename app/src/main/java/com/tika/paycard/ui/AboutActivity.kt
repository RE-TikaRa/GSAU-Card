package com.tika.paycard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tika.paycard.BuildConfig
import com.tika.paycard.R
import com.tika.paycard.data.UpdateChecker
import com.tika.paycard.databinding.ActivityAboutBinding
import kotlinx.coroutines.launch

/**
 * 关于软件页:版本与软件名、简介、联系方式、开源许可证与政策条款。
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorManager.applyOverlay(this)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topbar.topbarTitle.text = getString(R.string.about_title)
        binding.topbar.btnBack.setOnClickListener { finish() }

        binding.aboutVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)

        binding.rowEmail.setOnClickListener {
            openUri("mailto:" + getString(R.string.about_contact_email_value))
        }
        binding.rowGithub.setOnClickListener {
            openUri("https://github.com/RE-TikaRa")
        }
        binding.rowBilibili.setOnClickListener {
            openUri("https://m.bilibili.com/space/374412219")
        }
        binding.rowWechat.setOnClickListener {
            copyText(getString(R.string.about_contact_wechat_value))
            AppDialog.notice(binding.root, getString(R.string.about_wechat_copied))
        }

        binding.rowLicense.setOnClickListener {
            showText(getString(R.string.about_license_title), getString(R.string.about_license_content))
        }
        binding.rowPrivacy.setOnClickListener {
            showText(getString(R.string.about_privacy_title), getString(R.string.about_privacy_content))
        }
        binding.rowAgreement.setOnClickListener {
            showText(getString(R.string.about_agreement_title), getString(R.string.about_agreement_content))
        }

        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }
    }

    private fun checkUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        AppDialog.notice(binding.root, getString(R.string.about_checking))
        lifecycleScope.launch {
            val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
            binding.btnCheckUpdate.isEnabled = true
            when (result) {
                is UpdateChecker.Result.NewVersion -> AppDialog.confirm(
                    context = this@AboutActivity,
                    title = getString(R.string.about_new_version_title, result.version),
                    message = getString(R.string.about_new_version_message),
                    positiveText = getString(R.string.about_go_download),
                    onPositive = { openUri(result.pageUrl.ifBlank { result.apkUrl }) }
                )
                is UpdateChecker.Result.UpToDate ->
                    AppDialog.notice(binding.root, getString(R.string.about_up_to_date))
                is UpdateChecker.Result.Error ->
                    AppDialog.notice(binding.root, getString(R.string.about_check_failed, result.message))
            }
        }
    }

    private fun openUri(uri: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    }

    private fun showText(title: String, message: String) {
        AppDialog.message(this, title, message, getString(R.string.about_dialog_close))
    }
}
