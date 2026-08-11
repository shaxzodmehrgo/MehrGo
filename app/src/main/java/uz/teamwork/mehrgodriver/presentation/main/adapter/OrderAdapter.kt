package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.BASE_URL_ROUTE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_GONE
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.RoutePointsBinder
import uz.teamwork.mehrgodriver.common.httpLogLevel
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.data.remote.RouteApiService
import uz.teamwork.mehrgodriver.databinding.AdapterOrderBinding
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import uz.teamwork.mehrgodriver.domain.model.Order

class OrderAdapter(val listener: OnOrderClickListener, val myLatLng: LatLng?) :
    ListAdapter<Order, OrderAdapter.OrderViewHolder>(OrderDiffUtil()) {
    private val apiInterface = getInstance().create(RouteApiService::class.java)

    private lateinit var layoutInflater: LayoutInflater

    interface OnOrderClickListener {
        fun onOrderClick(orderId: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = AdapterOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        layoutInflater = LayoutInflater.from(parent.context)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class OrderViewHolder(val binding: AdapterOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                // When the order was placed — lets the driver see a stale pooled order at a glance.
                // Drop the "y" the server appends after the year ("Jul 24, 2026y 22:25"), matching
                // the order-history card. Hidden when the backend omits created_at.
                val created = item.createdAt?.datetime?.replace("y ", " ")
                if (!created.isNullOrBlank()) {
                    tvCreatedAt.text = created
                    llCreatedAt.visibility = View.VISIBLE
                } else {
                    llCreatedAt.visibility = View.GONE
                }

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

//                tvAddress.text = "${item.address?.name ?: itemView.context.getString(R.string.not_showed)} (${item.addressCategory?.name ?: itemView.context.getString(R.string.not_showed)})"
//                tvAddressFinish.text = "${item.addressFinish?.name ?: itemView.context.getString(R.string.not_showed)} (${item.addressCategoryFinish?.name ?: itemView.context.getString(R.string.not_showed)})"

                val pickupName = item.locations.firstOrNull()?.name
                tvAddress.text = if (!pickupName.isNullOrEmpty()) pickupName
                else itemView.context.getString(R.string.not_showed)

                val dropoffName = item.locations.takeIf { it.size > 1 }?.lastOrNull()?.name
                tvAddressFinish.text = if (!dropoffName.isNullOrEmpty()) dropoffName
                else itemView.context.getString(R.string.not_showed)

                // District subtitles removed; show ALL intermediate stops between pickup and dropoff.
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
                // Client name hidden on the order pool — not shown on new-order views.
                llClient.visibility = View.GONE

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
//                        tvDistanceTrack.text = distanceMetre.toString() + itemView.context.getString(R.string.metre)
//                    } else {
//                        tvDistanceTrack.text = Helper.metreToRoundKm(distanceMetre.toString()) + itemView.context.getString(R.string.km)
//                    }

                    tvDistanceTrack.text =
                        item.distance.toString() + itemView.context.getString(R.string.km)
                    // Trip-distance row removed from the order card per request — keep it hidden.
                    llVerticalLine.visibility = View.GONE
                } else {
                    tvDistanceTrack.text = ""
                    llVerticalLine.visibility = View.GONE
                }

                // No surge OR zero surge — the server ships 0 for plain orders; "0 so'm ↑" is noise.
                if ((item.addPrice ?: 0) <= 0) {
                    tvAddPrice.visibility = View.GONE
                    ivAddPrice.visibility = View.GONE

                    val roundTotalPrice = Helper.roundPrice(item.price.toLong())
                    binding.tvTotalPrice.text = Helper.formatPrice(roundTotalPrice) +
                            itemView.context.getString(R.string.sum)
                } else {
                    tvAddPrice.visibility = View.VISIBLE
                    ivAddPrice.visibility = View.VISIBLE
                    tvAddPrice.text =
                        Helper.formatPrice(item.addPrice.toString()) + itemView.context.getString(R.string.sum)

                    val roundTotalPrice = Helper.roundPrice(item.price.toLong())
                    binding.tvTotalPrice.text =
                        Helper.formatPrice(roundTotalPrice) + itemView.context.getString(R.string.sum)
                }


                if (item.state == ORDER_STATE_CHANGED_GONE) {
                    tvOrderActive.visibility = View.VISIBLE
                } else {
                    tvOrderActive.visibility = View.GONE
                }

//                if (item.tariff.commission != null) {
//                    tvCommission.visibility = View.VISIBLE
//                    tvCommission.text = itemView.context.getString(R.string.price_commission) +
//                            Helper.formatPrice(item.tariff.commission)
//                } else {
//                    tvCommission.visibility = View.GONE
//                }

                // Use the freshest driver location (the constructor snapshot can be null at the
                // moment the adapter is built, which would freeze the header on "Aniqlanmagan").
                val myLoc = MyTrackingService.lastLatLngWholeApp.value ?: myLatLng
                if (myLoc != null) {
//                    val clientLatLng = LatLng(item.latitude.toDouble(), item.longitude.toDouble())
//                    val distanceMetre = Helper.calculateBetweenTwoPoints(myLatLng, clientLatLng).toInt()
//
//                    if (distanceMetre < 1000) {
//                        tvDistanceClient.text = distanceMetre.toString() + itemView.context.getString(R.string.metre)
//                    } else {
//                        tvDistanceClient.text = Helper.metreToRoundKm(distanceMetre.toString()) + itemView.context.getString(R.string.km)
//                    }

                    tvDistanceClient.text = itemView.context.getString(R.string.checking)
                    tvDistanceTime.visibility = View.GONE
                    // Guard the async write against view recycling: only apply the result if this
                    // holder still shows the same order it was started for.
                    val expectedId = item.id
                    val call = apiInterface.getDistance(
                        myLoc.longitude,
                        myLoc.latitude,
                        item.longitude.toDouble(),
                        item.latitude.toDouble()
                    )
                    call.enqueue(object : Callback<DirectionLocations> {
                        @SuppressLint("SetTextI18n")
                        override fun onResponse(
                            call: Call<DirectionLocations>,
                            response: Response<DirectionLocations>
                        ) {
                            if (!stillShows(expectedId)) return
                            val route = response.body()?.routes?.firstOrNull()
                            if (route != null) {
                                val distanceMetre = route.distance
                                tvDistanceClient.text = if (distanceMetre < 1000) {
                                    distanceMetre.toString() + itemView.context.getString(R.string.metre)
                                } else {
                                    Helper.metreToRoundKm(distanceMetre.toString()) +
                                            itemView.context.getString(R.string.km)
                                }
                                // ETA to client at a 30 km/h city average (= 500 m/min) — SAME calc
                                // as the new-order view (AutoOfferService.getDistance).
                                val minutes = Math.round(distanceMetre / 500.0).toInt()
                                val minLabel = itemView.context.getString(R.string.minute)
                                tvDistanceTime.text =
                                    if (minutes < 1) "<1 $minLabel" else "~$minutes $minLabel"
                                tvDistanceTime.visibility = View.VISIBLE
                            } else {
                                tvDistanceClient.text =
                                    itemView.context.getString(R.string.not_defined)
                                tvDistanceTime.visibility = View.GONE
                            }
                        }

                        override fun onFailure(call: Call<DirectionLocations>, t: Throwable) {
                            if (!stillShows(expectedId)) return
                            tvDistanceClient.text = itemView.context.getString(R.string.not_defined)
                        }
                    })

                } else {
                    tvDistanceClient.text = itemView.context.getString(R.string.not_defined)
                    tvDistanceTime.visibility = View.GONE
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

                // Click
                llItem.setOnClickListener {
                    listener.onOrderClick(item.id)
                }

                // Get distance to client
            }
        }

        /** True while this holder still displays [orderId] — stops a late getDistance callback from
         *  writing into a row the RecyclerView has since rebound to another order. */
        private fun stillShows(orderId: Int): Boolean {
            val pos = bindingAdapterPosition
            return pos != RecyclerView.NO_POSITION && getItem(pos).id == orderId
        }
    }

    // DiffUtil
    class OrderDiffUtil : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }

    // Get distance
    // NOTE: this hand-rolls a SECOND Retrofit/OkHttp stack for the route server, duplicating the
    // @Named("retrofit_route") instance NetworkModule already provides. Left in place for now
    // (injecting into an adapter needs Hilt plumbing), but the logging level must come from the
    // same helper as the DI client — gating only one of the two left credentials in release logs.
    private fun getInstance(): Retrofit {
        val client = OkHttpClient()
        val interceptor = HttpLoggingInterceptor().setLevel(httpLogLevel())
        val clientBuilder: OkHttpClient.Builder = client.newBuilder().addInterceptor(interceptor)

        return Retrofit.Builder().baseUrl(BASE_URL_ROUTE)
            .addConverterFactory(GsonConverterFactory.create(uz.teamwork.mehrgodriver.common.AppGson.gson))
            .client(clientBuilder.build())
            .build()
    }
}