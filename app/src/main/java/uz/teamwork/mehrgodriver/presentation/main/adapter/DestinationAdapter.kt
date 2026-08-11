package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.databinding.AdapterDestinationBinding
import uz.teamwork.mehrgodriver.domain.model.Order

class DestinationAdapter(val listener: OnDestinationClickListener) :
    ListAdapter<Order.Location, DestinationAdapter.DestinationViewHolder>(DestinationDiffUtil()) {

    /** POSITION value of the stop currently being routed to (accent border). Null in full-route
     *  mode, where the separate "Full route" row is the accent one instead. Set before submitList. */
    var activeStopPosition: Int? = null

    interface OnDestinationClickListener {
        fun onDestinationClick(location: Order.Location)
        fun onDestinationMapClick(location: Order.Location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DestinationViewHolder {
        val view = AdapterDestinationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DestinationViewHolder(view)
    }

    override fun onBindViewHolder(holder: DestinationViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class DestinationViewHolder(val binding: AdapterDestinationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)
            val ctx = itemView.context

            binding.apply {
                // Letter badge by list order: A = pickup, B = first drop, C = next…
                tvLetter.text = ('A' + position).toString()

                tvName.text = when {
                    position == 0 && item.name.isNotEmpty() ->
                        "${item.name} (${ctx.getString(R.string.client_address)})"

                    position == 0 ->
                        "${ctx.getString(R.string.not_showed)} (${ctx.getString(R.string.client_address)})"

                    item.name.isNotEmpty() -> item.name
                    else -> ctx.getString(R.string.not_showed)
                }

                // Active target → accent border; every other stop → gray. (Replaces the old
                // radio-button-checked fill icon.)
                mcvRow.setStrokeColor(
                    ContextCompat.getColor(
                        ctx,
                        if (item.position == activeStopPosition) R.color.app_color
                        else R.color.gray_light_black_light
                    )
                )

                // Two actions per stop: Local (in-app re-route) and External (open in map app).
                mcvInApp.setOnClickListener { listener.onDestinationClick(item) }
                mcvMap.setOnClickListener { listener.onDestinationMapClick(item) }
            }
        }
    }

    // DiffUtil
    class DestinationDiffUtil : DiffUtil.ItemCallback<Order.Location>() {
        override fun areItemsTheSame(
            oldItem: Order.Location,
            newItem: Order.Location
        ): Boolean {
            return oldItem.latitude == newItem.latitude
        }

        override fun areContentsTheSame(
            oldItem: Order.Location,
            newItem: Order.Location
        ): Boolean {
            return oldItem == newItem
        }
    }
}
