package com.tika.paycard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tika.paycard.data.Account
import com.tika.paycard.databinding.ItemAccountBinding

/**
 * 账号列表:点击切换当前卡,长按删除。当前选中项高亮。
 */
class AccountAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    private var items: List<Account> = emptyList()
    private var currentIndex: Int = -1

    fun submit(list: List<Account>, current: Int) {
        items = list
        currentIndex = current
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val account = items[position]
        holder.binding.itemName.text = account.displayName()
        holder.binding.itemInfo.text = buildString {
            if (account.cardNo.isNotBlank()) append(account.cardNo)
            if (account.balance.isNotBlank()) {
                if (isNotEmpty()) append("  ")
                append("余额 ${account.balance}")
            }
        }
        holder.binding.itemCurrent.visibility =
            if (position == currentIndex) android.view.View.VISIBLE else android.view.View.INVISIBLE
        holder.binding.root.setOnClickListener { onClick(position) }
        holder.binding.root.setOnLongClickListener { onLongClick(position); true }
    }

    override fun getItemCount(): Int = items.size
}
