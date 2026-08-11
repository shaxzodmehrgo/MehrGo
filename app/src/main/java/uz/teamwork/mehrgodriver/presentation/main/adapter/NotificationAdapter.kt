package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.MediaUrl
import uz.teamwork.mehrgodriver.databinding.AdapterNotificationBinding
import uz.teamwork.mehrgodriver.domain.model.Notification

class NotificationAdapter(val listener: OnNotificationClickListener) :
    ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffUtil()) {

    private var context: Context? = null

    interface OnNotificationClickListener {
        fun onNotificationClick(notification: Notification)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = AdapterNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        context = parent.context
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class NotificationViewHolder(val binding: AdapterNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                Glide.with(context!!).load(MediaUrl.of(item.photoUrl))
                    .placeholder(R.drawable.ic_launcher_foreground).into(ivImage)
                tvTitle.text = item.message
                tvDate.text = item.createdAt.dateTime ?: ""

                llItem.setOnClickListener {
                    listener.onNotificationClick(item)
                }
            }
        }
    }

    // DiffUtil
    class NotificationDiffUtil : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(
            oldItem: Notification,
            newItem: Notification
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: Notification,
            newItem: Notification
        ): Boolean {
            return oldItem == newItem
        }
    }
}