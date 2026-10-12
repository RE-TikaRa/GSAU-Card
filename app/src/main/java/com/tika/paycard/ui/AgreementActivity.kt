package com.tika.paycard.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tika.paycard.R
import com.tika.paycard.data.UserConsent
import com.tika.paycard.databinding.ActivityAgreementBinding

class AgreementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgreementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UserConsent.hasAccepted(this)) {
            openMain()
            return
        }
        ColorManager.applyOverlay(this)
        binding = ActivityAgreementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.agreementPrivacy.setOnClickListener {
            AppDialog.message(
                this,
                getString(R.string.about_privacy_title),
                getString(R.string.about_privacy_content),
                getString(R.string.about_dialog_close)
            )
        }
        binding.agreementTerms.setOnClickListener {
            AppDialog.message(
                this,
                getString(R.string.about_agreement_title),
                getString(R.string.about_agreement_content),
                getString(R.string.about_dialog_close)
            )
        }
        binding.agreementCheck.setOnCheckedChangeListener { _, checked ->
            binding.agreementAccept.isEnabled = checked
        }
        binding.agreementAccept.setOnClickListener {
            if (!binding.agreementCheck.isChecked) return@setOnClickListener
            UserConsent.accept(this)
            openMain()
        }
        binding.agreementDecline.setOnClickListener { finishAffinity() }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
