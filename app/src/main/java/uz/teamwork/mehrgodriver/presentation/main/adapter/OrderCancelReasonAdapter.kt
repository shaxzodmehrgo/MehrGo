package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.databinding.AdapterOrderCancelReasonBinding
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason

class OrderCancelReasonAdapter(val listener: OnOrderCancelReasonClickListener) :
    ListAdapter<OrderCancelReason, OrderCancelReasonAdapter.OrderCancelReasonViewHolder>(
        OrderCancelReasonDiffUtil()
    ) {

    interface OnOrderCancelReasonClickListener {
        fun onOrderCancelReasonClick(orderCancelReason: OrderCancelReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderCancelReasonViewHolder {
        val view = AdapterOrderCancelReasonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderCancelReasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderCancelReasonViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class OrderCancelReasonViewHolder(val binding: AdapterOrderCancelReasonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun onBind(position: Int) {
            val item = getItem(position)

            // The item root IS the chip (a TextView) — selection is expressed purely through
            // isSelected, so the border/wash/label swap lives in the drawable + colour selectors
            // (bg_chip_choice / chip_choice_text) instead of being poked from here.
            binding.root.apply {
                text = item.name ?: "—"
                isSelected = item.isChecked
                setDebouncedClickListener { listener.onOrderCancelReasonClick(item) }
            }
        }
    }

    // DiffUtil
    class OrderCancelReasonDiffUtil : DiffUtil.ItemCallback<OrderCancelReason>() {
        override fun areItemsTheSame(
            oldItem: OrderCancelReason,
            newItem: OrderCancelReason
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: OrderCancelReason,
            newItem: OrderCancelReason
        ): Boolean {
            return oldItem == newItem
        }
    }
}