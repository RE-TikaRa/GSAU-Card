package com.tika.paycard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tika.paycard.R
import com.tika.paycard.databinding.ActivitySettingsBinding
import com.tika.paycard.work.KeepAlive

/**
 * 设置页:保活档位选择 + 电池白名单跳转 + 各家 ROM 自启动引导。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        when (KeepAlive.getMode(this)) {
            KeepAlive.Mode.LITE -> binding.radioLite.isChecked = true
            KeepAlive.Mode.STEADY -> binding.radioSteady.isChecked = true
        }

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioSteady.id -> KeepAlive.Mode.STEADY
                else -> KeepAlive.Mode.LITE
            }
            KeepAlive.setMode(this, mode)
            val tip = if (mode == KeepAlive.Mode.STEADY)
                R.string.settings_mode_steady_tip
            else
                R.string.settings_mode_lite_tip
            Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
        }

        binding.btnBattery.setOnClickListener {
            KeepAlive.requestIgnoreBattery(this)
        }
        binding.btnAutostart.setOnClickListener {
            KeepAlive.openAutoStartSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.batteryStatus.text = getString(
            if (KeepAlive.isIgnoringBattery(this)) R.string.settings_battery_on
            else R.string.settings_battery_off
        )
    }
}
