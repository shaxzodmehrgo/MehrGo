package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import uz.teamwork.mehrgodriver.databinding.AdapterOrderHistoryFooterBinding

/** Footer shown under the order-history list: a spinner while the next page loads,
 *  and a tappable "retry" when a page fails. */
class OrderHistoryLoadStateAdapter(
    private val retry: () -> Unit
) : LoadStateAdapter<OrderHistoryLoadStateAdapter.FooterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): FooterViewHolder {
        val binding = AdapterOrderHistoryFooterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FooterViewHolder(binding, retry)
    }

    override fun onBindViewHolder(holder: FooterViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    class FooterViewHolder(
        private val binding: AdapterOrderHistoryFooterBinding,
        retry: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.tvFooterRetry.setOnClickListener { retry() }
        }

        fun bind(loadState: LoadState) {
            binding.pbFooter.visibility =
                if (loadState is LoadState.Loading) View.VISIBLE else View.GONE
            binding.tvFooterRetry.visibility =
                if (loadState is LoadState.Error) View.VISIBLE else View.GONE
        }
    }
}
