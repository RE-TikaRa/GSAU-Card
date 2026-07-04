package com.tika.paycard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
                "已开启前台服务保活（通知栏会有一条常驻通知）"
            else
                "已切到省心档（无常驻通知）"
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
        binding.batteryStatus.text = if (KeepAlive.isIgnoringBattery(this))
            "已加入电池白名单" else "未加入电池白名单（建议开启）"
    }
}
