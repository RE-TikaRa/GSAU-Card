package com.tika.paycard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tika.paycard.R
import com.tika.paycard.data.AccountBackup
import com.tika.paycard.data.AccountStore
import com.tika.paycard.databinding.ActivitySettingsBinding
import com.tika.paycard.widget.PayWidgetProvider
import com.tika.paycard.work.KeepAlive
import com.tika.paycard.work.WidgetExpiry
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页:保活档位选择 + 电池白名单跳转 + 各家 ROM 自启动引导。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportConfig(uri)
        }

    private val importDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importConfig(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorManager.applyOverlay(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topbar.topbarTitle.text = getString(R.string.settings_title)
        binding.topbar.btnBack.setOnClickListener { finish() }

        when (ThemeManager.getMode(this)) {
            ThemeManager.Mode.SYSTEM -> binding.themeSystem.isChecked = true
            ThemeManager.Mode.LIGHT -> binding.themeLight.isChecked = true
            ThemeManager.Mode.DARK -> binding.themeDark.isChecked = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.themeLight.id -> ThemeManager.Mode.LIGHT
                binding.themeDark.id -> ThemeManager.Mode.DARK
                else -> ThemeManager.Mode.SYSTEM
            }
            if (mode != ThemeManager.getMode(this)) ThemeManager.setMode(this, mode)
        }

        setupColorSwatches()

        binding.btnExport.setOnClickListener { exportDocument.launch("GSAU-Card-config.json") }
        binding.btnImport.setOnClickListener { importDocument.launch(arrayOf("application/json", "text/plain")) }

        when (KeepAlive.getMode(this)) {
            KeepAlive.Mode.LITE -> binding.radioLite.isChecked = true
            KeepAlive.Mode.STEADY -> {
                binding.radioSteady.isChecked = true
                ensureNotificationPermission()
            }
        }

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioSteady.id -> KeepAlive.Mode.STEADY
                else -> KeepAlive.Mode.LITE
            }
            KeepAlive.setMode(this, mode)
            PayWidgetProvider.refreshAll(this)
            if (mode == KeepAlive.Mode.STEADY) ensureNotificationPermission()
            val tip = if (mode == KeepAlive.Mode.STEADY)
                R.string.settings_mode_steady_tip
            else
                R.string.settings_mode_lite_tip
            AppDialog.notice(binding.root, getString(tip))
        }

        binding.btnBattery.setOnClickListener {
            KeepAlive.requestIgnoreBattery(this)
        }
        binding.btnAutostart.setOnClickListener {
            KeepAlive.openAutoStartSettings(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.btnAlarm.setOnClickListener {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } else {
            binding.btnAlarm.visibility = View.GONE
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun exportConfig(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = AccountStore.get(this@SettingsActivity).exportConfig()
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IOException(getString(R.string.config_file_unavailable))
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                AppDialog.notice(binding.root, getString(R.string.config_exported))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: getString(R.string.config_file_unavailable)
                AppDialog.notice(binding.root, getString(R.string.config_export_failed, message))
            }
        }
    }

    private fun importConfig(uri: Uri) {
        lifecycleScope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.config_file_unavailable))
                    input.bufferedReader(Charsets.UTF_8).use { AccountBackup.decode(it.readText()) }
                }
                if (config.accounts.isEmpty()) {
                    AppDialog.notice(binding.root, getString(R.string.config_empty))
                    return@launch
                }
                AppDialog.confirm(
                    context = this@SettingsActivity,
                    title = getString(R.string.config_import_title),
                    message = getString(R.string.config_import_message, config.accounts.size),
                    positiveText = getString(R.string.config_import_button),
                    onPositive = {
                        val added = AccountStore.get(this@SettingsActivity).importConfig(config)
                        KeepAlive.apply(this@SettingsActivity)
                        PayWidgetProvider.refreshAll(this@SettingsActivity)
                        AppDialog.notice(binding.root, getString(R.string.config_imported, added))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppDialog.notice(binding.root, getString(R.string.config_import_failed))
            }
        }
    }

    private fun setupColorSwatches() {
        val swatches = listOf(
            Triple(binding.swatchGrayGreen, binding.dotGrayGreen, ColorManager.Scheme.GRAY_GREEN),
            Triple(binding.swatchGrayBlue, binding.dotGrayBlue, ColorManager.Scheme.GRAY_BLUE),
            Triple(binding.swatchLotusPink, binding.dotLotusPink, ColorManager.Scheme.LOTUS_PINK),
            Triple(binding.swatchTerracotta, binding.dotTerracotta, ColorManager.Scheme.TERRACOTTA),
            Triple(binding.swatchGrayMauve, binding.dotGrayMauve, ColorManager.Scheme.GRAY_MAUVE)
        )
        val current = ColorManager.getScheme(this)
        swatches.forEach { (hit, dot, scheme) ->
            dot.foreground = if (scheme == current)
                ContextCompat.getDrawable(this, R.drawable.swatch_ring_on) else null
            hit.setOnClickListener {
                if (scheme != ColorManager.getScheme(this)) {
                    ColorManager.setScheme(this, scheme)
                    PayWidgetProvider.refreshAll(this)
                    recreate()
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        binding.batteryStatus.text = getString(
            if (KeepAlive.isIgnoringBattery(this)) R.string.settings_battery_on
            else R.string.settings_battery_off
        )
        binding.alarmStatus.text = getString(
            if (WidgetExpiry.canSchedule(this)) R.string.settings_alarm_on
            else R.string.settings_alarm_off
        )
        PayWidgetProvider.refreshAll(this)
    }
}
