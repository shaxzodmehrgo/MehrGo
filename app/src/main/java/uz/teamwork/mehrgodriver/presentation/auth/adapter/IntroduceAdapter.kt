package uz.teamwork.mehrgodriver.presentation.auth.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.common.MediaUrl
import uz.teamwork.mehrgodriver.databinding.AdapterIntroduceBinding
import uz.teamwork.mehrgodriver.domain.model.Introduce

class IntroduceAdapter(private var list: List<Introduce>) :
    RecyclerView.Adapter<IntroduceAdapter.SliderAdapterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderAdapterViewHolder {
        val view =
            AdapterIntroduceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SliderAdapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderAdapterViewHolder, position: Int) {
        holder.onBind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onViewAttachedToWindow(holder: SliderAdapterViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.playEntrance()
    }

    override fun onViewDetachedFromWindow(holder: SliderAdapterViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.reset()
    }

    // ViewHolder
    inner class SliderAdapterViewHolder(val binding: AdapterIntroduceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var breathing: AnimatorSet? = null

        fun onBind(item: Introduce) {
            binding.apply {
                Glide.with(itemView.context)
                    .load(MediaUrl.of(item.imageUrl))
                    .fitCenter()
                    .into(ivImage)
                tvHeader.text = item.header
                tvTitle.text = item.title
                tvDesc.text = item.description
            }
        }

        // Spring-style entrance for the medallion + a gentle rise/fade for the text,
        // then a continuous "breathing" pulse on the medallion (matches the iOS introduce).
        fun playEntrance() {
            val medallion = binding.flMedallion
            val content = binding.llContent

            medallion.animate().cancel()
            medallion.scaleX = 0.85f
            medallion.scaleY = 0.85f
            medallion.alpha = 0f
            medallion.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(380)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { startBreathing() }
                .start()

            content.animate().cancel()
            content.alpha = 0f
            content.translationY = 24f
            content.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(120)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        private fun startBreathing() {
            breathing?.cancel()
            val medallion = binding.flMedallion
            val scaleX = ObjectAnimator.ofFloat(medallion, View.SCALE_X, 1f, 1.06f)
            val scaleY = ObjectAnimator.ofFloat(medallion, View.SCALE_Y, 1f, 1.06f)
            listOf(scaleX, scaleY).forEach {
                it.duration = 1500
                it.repeatCount = ValueAnimator.INFINITE
                it.repeatMode = ValueAnimator.REVERSE
                it.interpolator = AccelerateDecelerateInterpolator()
            }
            breathing = AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                start()
            }
        }

        fun reset() {
            breathing?.cancel()
            breathing = null
            binding.flMedallion.animate().cancel()
            binding.flMedallion.scaleX = 1f
            binding.flMedallion.scaleY = 1f
            binding.flMedallion.alpha = 1f
            binding.flMedallion.translationX = 0f
            binding.llContent.animate().cancel()
            binding.llContent.alpha = 1f
            binding.llContent.translationY = 0f
        }
    }
}
