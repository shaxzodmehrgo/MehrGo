package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.MediaUrl
import uz.teamwork.mehrgodriver.databinding.AdapterTariffBinding
import uz.teamwork.mehrgodriver.domain.model.Tariff

class TariffAdapter(val listener: OnTariffClickListener) :
    ListAdapter<Tariff, TariffAdapter.TariffViewHolder>(TariffDiffUtil()) {
    interface OnTariffClickListener {
        fun onTariffClick(tariff: Tariff)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TariffViewHolder {
        val view = AdapterTariffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TariffViewHolder(view)
    }

    override fun onBindViewHolder(holder: TariffViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class TariffViewHolder(val binding: AdapterTariffBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                tvName.text = item.name
                tvPrice.text = "${Helper.formatPrice(item.startingPrice.toString())}${
                    binding.root.context.getString(R.string.sum)
                }"

                // Was `IMAGE_URL + icon.drop(1)`, which built "https://prod.mehrgo.uzuploads/…" —
                // an unresolvable host, so every tariff had been showing the example_taxi
                // placeholder instead of its real icon. MediaUrl joins the two shapes correctly.
                Glide.with(itemView.context)
                    .load(MediaUrl.of(item.icon))
                    .error(R.drawable.example_taxi)
                    .into(binding.ivImage)


                // Selected card gets an app-colour border (alongside the radio) for a clearer,
                // at-a-glance selection state; unselected stays the neutral hairline.
                val density = itemView.resources.displayMetrics.density
                if (item.isSelected) {
                    ivChecked.visibility = View.VISIBLE
                    ivNotChecked.visibility = View.GONE
                    flItem.strokeColor = ContextCompat.getColor(itemView.context, R.color.app_color)
                    flItem.strokeWidth = (2 * density).toInt()
                } else {
                    ivChecked.visibility = View.GONE
                    ivNotChecked.visibility = View.VISIBLE
                    flItem.strokeColor =
                        ContextCompat.getColor(itemView.context, R.color.gray_middle_black_middle)
                    flItem.strokeWidth = (1 * density).toInt()
                }

                flItem.setOnClickListener {
                    listener.onTariffClick(item)
                }
            }
        }
    }

    // DiffUtil
    class TariffDiffUtil : DiffUtil.ItemCallback<Tariff>() {
        override fun areItemsTheSame(oldItem: Tariff, newItem: Tariff): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Tariff, newItem: Tariff): Boolean {
            return oldItem == newItem
        }
    }
}