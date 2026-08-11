package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.databinding.AdapterVideoBinding
import uz.teamwork.mehrgodriver.domain.model.Video

class VideoAdapter(val listener: OnVideoClickListener) :
    ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffUtil()) {

    interface OnVideoClickListener {
        fun onVideoClick(video: Video)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = AdapterVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.onBind(position)
    }

    // ViewHolder
    inner class VideoViewHolder(val binding: AdapterVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(position: Int) {
            val item = getItem(position)

            binding.apply {
                Glide.with(itemView.context).load(item.poster ?: "")
                    .placeholder(R.drawable.ic_launcher_foreground).into(ivImage)
                tvTitle.text = item.title

                llItem.setOnClickListener {
                    listener.onVideoClick(item)
                }
            }
        }
    }

    // DiffUtil
    class VideoDiffUtil : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(
            oldItem: Video,
            newItem: Video
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: Video,
            newItem: Video
        ): Boolean {
            return oldItem == newItem
        }
    }
}