package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.databinding.AdapterSubscriptionBinding
import uz.teamwork.mehrgodriver.domain.model.SubscriptionData
import java.util.concurrent.TimeUnit

class SubscriptionAdapter(val listener: OnSubscriptionClickListener) :
    PagingDataAdapter<SubscriptionData.Subscription, SubscriptionAdapter.SubscriptionViewHolder>(
        SubscriptionDiffUtil()
    ) {
    lateinit var context: Context
    private lateinit var layoutInflater: LayoutInflater

    interface OnSubscriptionClickListener {
        fun onSubscriptionClick(id: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val view =
            AdapterSubscriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        context = parent.context
        layoutInflater = LayoutInflater.from(parent.context)
        return SubscriptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        holder.onBind(getItem(position)!!, position)
    }

    // ViewHolder
    inner class SubscriptionViewHolder(val binding: AdapterSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(item: SubscriptionData.Subscription, position: Int) {

            binding.apply {
                tvName.text = item.name

                val desc = item.descriptions ?: ""
                if (desc.isNotEmpty()) {
                    tvDesc.text = item.descriptions
                } else {
                    tvDesc.visibility = View.GONE
                }

                val text = if (item.purchased == null) {
                    context.getString(R.string.activate)
                } else {
                    context.getString(R.string.add)
                }
                tvSubscribe.text =
                    "$text ${Helper.formatPrice(item.price.toString()) + context.getString(R.string.sum)} / ${
                        convert(item.validityPeriod)
                    }"

                val tariffs = item.tariffs ?: emptyList()
                chipGroup.removeAllViews()
                for (tariff in tariffs) {
                    val chip =
                        layoutInflater.inflate(R.layout.adapter_chip, null, false) as TextView
                    chip.text = tariff.name
                    chipGroup.addView(chip)
                }

                if (item.purchased != null) {
                    tvRemainingDay.text =
                        "${convert(item.purchased.expiresIn)} ${context.getString(R.string.left)}"
                } else {
                    tvPurchased.visibility = View.GONE
                }

                tvSubscribe.setOnClickListener {
                    listener.onSubscriptionClick(item.id)
                }
            }
        }

        private fun convert(second: Long): String {
            val days = TimeUnit.SECONDS.toDays(second)
            val hours = TimeUnit.SECONDS.toHours(second) % 24
            val minutes = TimeUnit.SECONDS.toMinutes(second) % 60
            val seconds = second % 60

            return when {
                days > 0 -> "$days ${context.getString(R.string.day)}"
                hours > 0 -> "$hours ${context.getString(R.string.hour)}"
                minutes > 0 -> "$minutes ${context.getString(R.string.minute)}"
                else -> "$seconds ${context.getString(R.string.second)}"
            }
        }

    }

    // DiffUtil
    class SubscriptionDiffUtil : DiffUtil.ItemCallback<SubscriptionData.Subscription>() {
        override fun areItemsTheSame(
            oldItem: SubscriptionData.Subscription,
            newItem: SubscriptionData.Subscription
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: SubscriptionData.Subscription,
            newItem: SubscriptionData.Subscription
        ): Boolean {
            return oldItem == newItem
        }
    }
}