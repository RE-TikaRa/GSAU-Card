package com.tika.paycard.ui

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tika.paycard.R
import com.tika.paycard.data.ImageLoader
import com.tika.paycard.databinding.ActivityGuideBinding
import kotlinx.coroutines.launch

/**
 * 首启操作引导:绑定付款码链接与添加桌面卡片的分步图文。
 * 截图走反代远程加载,看完点开始使用进主界面。
 */
class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorManager.applyOverlay(this)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topbar.topbarTitle.text = getString(R.string.guide_title)
        binding.topbar.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { finish() }

        loadInto(binding.guideImage1, GUIDE_IMAGES[0])
        loadInto(binding.guideImage2, GUIDE_IMAGES[1])
        loadInto(binding.guideImage3, GUIDE_IMAGES[2])
    }

    private fun loadInto(view: ImageView, url: String) {
        lifecycleScope.launch {
            ImageLoader.load(url)?.let { view.setImageBitmap(it) }
        }
    }

    companion object {
        private const val RAW = "https://gh.re-tikara.fun/raw/RE-TikaRa/ImgHosting/main"
        private val GUIDE_IMAGES = (1..3).map {
            "$RAW/2026%E5%B9%B47%E6%9C%885%E6%97%A5%20GSAU-Card%E6%95%99%E7%A8%8B%20($it).jpg"
        }
    }
}
