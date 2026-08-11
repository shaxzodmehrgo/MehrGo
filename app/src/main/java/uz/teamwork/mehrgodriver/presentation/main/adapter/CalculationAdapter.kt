package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.location.Location
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.databinding.AdapterOrderInLocaleBinding
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation

class CalculationAdapter(val listener: OnCalculationClickListener) :
    ListAdapter<Calculation, CalculationAdapter.CalculationViewHolder>(CalculationDiffUtil()) {
    interface OnCalculationClickListener {
        fun onCalculationClick(calculation: Calculation)
    }

    /** Row id → total track length in metres. Summing hundreds of GPS points on EVERY bind made
     *  scrolling heavy; a saved calculation is immutable, so compute once and reuse. */
    private val distanceCache = HashMap<Int, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalculationViewHolder {
        val view = AdapterOrderInLocaleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return CalculationViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalculationViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class CalculationViewHolder(val binding: AdapterOrderInLocaleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                if (item.locations.size > 1) {
                    val distanceMetre = distanceCache.getOrPut(item.id) {
                        calculateBetweenAllPoints(item.locations).toInt()
                    }
                    if (distanceMetre < 1000) {
                        tvDistance.text =
                            distanceMetre.toString() + itemView.context.getString(R.string.metre)
                    } else {
                        tvDistance.text =
                            Helper.metreToRoundKm(distanceMetre.toString()) + itemView.context.getString(
                                R.string.km
                            )
                    }
                } else {
                    tvDistance.text = itemView.context.getString(R.string.not_defined)
                }

                tvOrderId.text = "ID: ${item.orderId}"

                // Labels ("Yo'l vaqti" / "Kutilgan vaqt") are now static in the layout —
                // set the value only, on its own line, so long times never wrap mid-label.
                binding.tvTrackTime.text = Helper.formatTime(item.trackedTime)
                binding.tvWaitedTime.text = Helper.formatTime(item.waitedTime)

                // Whole card is tappable (was only the distance text).
                binding.root.setOnClickListener {
                    listener.onCalculationClick(item)
                }
            }
        }
    }

    // DiffUtil
    class CalculationDiffUtil : DiffUtil.ItemCallback<Calculation>() {
        override fun areItemsTheSame(
            oldItem: Calculation,
            newItem: Calculation
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: Calculation,
            newItem: Calculation
        ): Boolean {
            return oldItem == newItem
        }
    }

    private fun calculateBetweenTwoPoints(latLng1: LatLng, latLng2: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            latLng1.latitude,
            latLng1.longitude,
            latLng2.latitude,
            latLng2.longitude,
            result
        )

        return result[0]
    }

    private fun calculateBetweenAllPoints(locations: List<MyLocation>): Float {
        var result = 0f

        for (index in 0..locations.size - 2) {
            val current = locations[index]
            val next = locations[index + 1]

            result += calculateBetweenTwoPoints(
                LatLng(current.latitude, current.longitude),
                LatLng(next.latitude, next.longitude)
            )
        }

        return result
    }
}