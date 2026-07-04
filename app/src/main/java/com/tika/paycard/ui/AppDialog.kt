package com.tika.paycard.ui

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.app.Dialog
import com.tika.paycard.R
import com.tika.paycard.databinding.DialogAppBinding

/**
 * 应用内统一弹窗:输入、确认、警告共用一套外观与动效,替代系统原生 AlertDialog。
 */
object AppDialog {

    /** 确认类弹窗:标题 + 描述 + 确认/取消。 */
    fun confirm(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        positiveText: CharSequence,
        onPositive: () -> Unit,
        negativeText: CharSequence = context.getString(R.string.cancel)
    ) {
        build(context) { binding, dismiss ->
            binding.dialogTitle.text = title
            binding.dialogMessage.text = message
            binding.dialogPositive.text = positiveText
            binding.dialogNegative.text = negativeText
            binding.dialogPositive.setOnClickListener { dismiss(); onPositive() }
            binding.dialogNegative.setOnClickListener { dismiss() }
        }
    }

    /** 输入类弹窗:标题 + 描述 + 单行输入框 + 确认/取消,回调输入文本。 */
    fun input(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        hint: CharSequence,
        positiveText: CharSequence,
        onPositive: (String) -> Unit,
        negativeText: CharSequence = context.getString(R.string.cancel)
    ) {
        build(context) { binding, dismiss ->
            binding.dialogTitle.text = title
            binding.dialogMessage.text = message
            binding.dialogInput.visibility = android.view.View.VISIBLE
            binding.dialogInput.hint = hint
            binding.dialogInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            binding.dialogPositive.text = positiveText
            binding.dialogNegative.text = negativeText
            binding.dialogPositive.setOnClickListener {
                val text = binding.dialogInput.text.toString()
                dismiss(); onPositive(text)
            }
            binding.dialogNegative.setOnClickListener { dismiss() }
            binding.dialogInput.requestFocus()
        }
    }

    private inline fun build(
        context: Context,
        bind: (DialogAppBinding, dismiss: () -> Unit) -> Unit
    ) {
        val binding = DialogAppBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context, R.style.Dialog_PayCard).apply {
            setContentView(binding.root)
            window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bind(binding) { dialog.dismiss() }
        dialog.show()
    }
}
