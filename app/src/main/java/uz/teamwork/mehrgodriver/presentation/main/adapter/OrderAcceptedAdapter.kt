package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_ARRIVED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_GONE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_STARTED
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.RoutePointsBinder
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.databinding.AdapterOrderBinding
import uz.teamwork.mehrgodriver.domain.model.Order

class OrderAcceptedAdapter(val listener: OnOrderAcceptedClickListener, val myLatLng: LatLng?) :
    ListAdapter<Order, OrderAcceptedAdapter.OrderAcceptedViewHolder>(OrderAcceptedDiffUtil()) {
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var context: Context

    interface OnOrderAcceptedClickListener {
        fun onOrderAcceptedClick(order: Order)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderAcceptedViewHolder {
        val view = AdapterOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        context = parent.context
        layoutInflater = LayoutInflater.from(parent.context)
        return OrderAcceptedViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderAcceptedViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class OrderAcceptedViewHolder(val binding: AdapterOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                // Payment chip — colour-coded like the trip detail: cash = green, card = blue.
                val payColor: Int
                val paySoft: Int
                if (item.isCardPayment == true) {
                    ivPaymentType.setImageResource(R.drawable.icon_card)
                    tvPaymentType.text = itemView.context.getString(R.string.payment_type_card)
                    payColor = ContextCompat.getColor(itemView.context, R.color.blue_middle)
                    paySoft = ContextCompat.getColor(itemView.context, R.color.blue_soft)
                } else {
                    ivPaymentType.setImageResource(R.drawable.icon_cash)
                    tvPaymentType.text = itemView.context.getString(R.string.payment_type_cash)
                    payColor = ContextCompat.getColor(itemView.context, R.color.green)
                    paySoft = ContextCompat.getColor(itemView.context, R.color.green_soft)
                }
                ivPaymentType.setColorFilter(payColor)
                tvPaymentType.setTextColor(payColor)
                llPaymentChip.backgroundTintList = ColorStateList.valueOf(paySoft)

                tvTariff.text = item.tariff.name ?: ""
//                tvAddress.text = "${item.address?.name ?: context.getString(R.string.not_showed)} (${item.addressCategory?.name ?: context.getString(R.string.not_showed)})"
//                tvAddressFinish.text = "${item.addressFinish?.name ?: context.getString(R.string.not_showed)} (${item.addressCategoryFinish?.name ?: context.getString(R.string.not_showed)})"

                if (item.locations[0].name.isNotEmpty()) {
                    tvAddress.text = item.locations[0].name
                } else {
                    tvAddress.text = itemView.context.getString(R.string.not_showed)
                }

                if (item.locations.size > 1 && item.locations.last().name.isNotEmpty()) {
                    tvAddressFinish.text = item.locations.last().name
                } else {
                    tvAddressFinish.text = itemView.context.getString(R.string.not_showed)
                }

                // District subtitles removed; show ALL intermediate stops between pickup and dropoff
                // (same as the new-order / pool view).
                tvAddressDistrict.visibility = View.GONE
                tvAddressFinishDistrict.visibility = View.GONE
                RoutePointsBinder.bindMiddle(
                    llMiddlePoints,
                    item.locations.drop(1).dropLast(1)
                        .map {
                            (it.name
                                ?: "").ifEmpty { itemView.context.getString(R.string.not_showed) }
                        },
                    R.layout.item_route_point_list
                )
                // Client (hidden for pool orders that have no contact yet).
                val clientName = item.contact?.name
                if (clientName.isNullOrEmpty()) {
                    llClient.visibility = View.GONE
                } else {
                    tvClientName.text = clientName
                    llClient.visibility = View.VISIBLE
                }

                // The comment is its own labelled section now, so toggle the section, not the text.
                if (!item.info.isNullOrEmpty()) {
                    tvInfo.text = item.info
                    llInfoSection.visibility = View.VISIBLE
                } else {
                    llInfoSection.visibility = View.GONE
                }

                if (item.locations.size > 1) {
//                    val distanceMetre = Helper.calculateBetweenAllPoints(item.locations).toInt()
//                    if (distanceMetre < 1000) {
//                        tvDistanceTrack.text = distanceMetre.toString() + context.getString(R.string.metre)
//                    } else {
//                        tvDistanceTrack.text = Helper.metreToRoundKm(distanceMetre.toString()) + context.getString(R.string.km)
//                    }

                    tvDistanceTrack.text = item.distance.toString() + context.getString(R.string.km)
                } else {
                    tvDistanceTrack.text = ""
                }

                if (item.addPrice == null) {
                    tvAddPrice.visibility = View.GONE
                    ivAddPrice.visibility = View.GONE

                    val roundTotalPrice = Helper.roundPrice(item.price.toLong())
                    binding.tvTotalPrice.text = Helper.formatPrice(roundTotalPrice) +
                            context.getString(R.string.sum)
                } else {
                    tvAddPrice.visibility = View.VISIBLE
                    ivAddPrice.visibility = View.VISIBLE
                    tvAddPrice.text = Helper.formatPrice(item.addPrice.toString()) +
                            context.getString(R.string.sum)

                    val roundTotalPrice = Helper.roundPrice(item.price.toLong())
                    binding.tvTotalPrice.text = Helper.formatPrice(roundTotalPrice) +
                            context.getString(R.string.sum)
                }


                if (item.state == ORDER_STATE_CHANGED_GONE) {
                    tvOrderActive.visibility = View.VISIBLE
                } else {
                    tvOrderActive.visibility = View.GONE
                }

//                if (item.tariff.commission != null) {
//                    tvCommission.visibility = View.VISIBLE
//                    tvCommission.text = context.getString(R.string.price_commission) + Helper.formatPrice(item.tariff.commission)
//                } else {
//                    tvCommission.visibility = View.GONE
//                }

                // Use the freshest driver location (the constructor snapshot can be null at the
                // moment the adapter is built, which would freeze the header on "Aniqlanmagan").
                val myLoc = MyTrackingService.lastLatLngWholeApp.value ?: myLatLng
                tvDistanceTime.visibility = View.GONE
                if (myLoc != null) {
                    val clientLatLng = LatLng(item.latitude.toDouble(), item.longitude.toDouble())
                    val distanceMetre =
                        Helper.calculateBetweenTwoPoints(myLoc, clientLatLng).toInt()
                    tvDistanceClient.text = if (distanceMetre < 1000) {
                        distanceMetre.toString() + context.getString(R.string.metre)
                    } else {
                        Helper.metreToRoundKm(distanceMetre.toString()) + context.getString(R.string.km)
                    }
                    // ETA to client at a 30 km/h city average (= 500 m/min) — SAME calc as the
                    // new-order view (AutoOfferService.getDistance).
                    val minutes = Math.round(distanceMetre / 500.0).toInt()
                    val minLabel = context.getString(R.string.minute)
                    tvDistanceTime.text = if (minutes < 1) "<1 $minLabel" else "~$minutes $minLabel"
                    tvDistanceTime.visibility = View.VISIBLE
                } else {
                    tvDistanceClient.text = context.getString(R.string.not_defined)
                }

                val services = item.services ?: emptyList()
                chipGroup.removeAllViews()
                llServicesSection.visibility = if (services.isEmpty()) View.GONE else View.VISIBLE
                for (service in services) {
                    val chip =
                        layoutInflater.inflate(R.layout.adapter_chip, null, false) as TextView
                    chip.text = service.service.name
                    chipGroup.addView(chip)
                }
                // Divider between the route points and the extras — only when the extras
                // section (comment / service chips) actually has content.
                vExtrasDivider.visibility =
                    if (!item.info.isNullOrEmpty() || services.isNotEmpty()) View.VISIBLE
                    else View.GONE
                // …and the hairline BETWEEN the two extras only when both are actually there.
                vInfoServicesDivider.visibility =
                    if (!item.info.isNullOrEmpty() && services.isNotEmpty()) View.VISIBLE
                    else View.GONE

                when (item.state) {
                    ORDER_STATE_ACCEPTED -> {
                        llVerticalLine.visibility = View.GONE
                        llAddressFinish.visibility = View.GONE
                    }

                    ORDER_STATE_STARTED -> {
                        llVerticalLine.visibility = View.GONE
                        llAddressFinish.visibility = View.GONE
                    }

                    ORDER_STATE_CHANGED_ARRIVED -> {
                        // Trip-distance row removed from the order card per request — keep it hidden;
                        // the dropoff address (llAddressFinish) still shows for these states.
                        llVerticalLine.visibility = View.GONE
                        llAddressFinish.visibility = View.VISIBLE
                    }

                    ORDER_STATE_CHANGED_GONE -> {
                        // Trip-distance row removed from the order card per request — keep it hidden;
                        // the dropoff address (llAddressFinish) still shows for these states.
                        llVerticalLine.visibility = View.GONE
                        llAddressFinish.visibility = View.VISIBLE
                    }
                }

                llItem.setOnClickListener {
                    listener.onOrderAcceptedClick(item)
                }
            }
        }
    }

    // DiffUtil
    class OrderAcceptedDiffUtil : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}