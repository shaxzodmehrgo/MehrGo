package uz.teamwork.mehrgodriver.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.InfoPopup.confirm
import uz.teamwork.mehrgodriver.databinding.DialogInfoPopupBinding

/**
 * Reusable centered info / warning popup (iOS-style) built on [DialogInfoPopupBinding] —
 * soft circular glyph, bold title, optional message and a single full-width OK button.
 *
 * Unlike `MapFragment.showInfoPopup` (which is map-front-guarded and takes only string
 * resources), this variant accepts a dynamic [message] String so callers can surface a
 * server error verbatim — e.g. the "you already have an active order" reason the backend
 * returns when a driver tries to accept a second order.
 *
 * When a [lifecycle] is supplied the dialog auto-dismisses on ON_DESTROY, so a caller can't
 * leak a window if its host (fragment / activity) is torn down while the popup is up.
 */
object InfoPopup {
    fun show(
        context: Context,
        title: String,
        message: String? = null,
        @DrawableRes iconRes: Int = R.drawable.ic_error_circle,
        okText: String? = null,
        cancelable: Boolean = true,
        lifecycle: Lifecycle? = null,
        onOk: (() -> Unit)? = null,
    ): Dialog {
        val binding = DialogInfoPopupBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(cancelable)
            setCanceledOnTouchOutside(cancelable)
        }

        binding.ivIcon.setImageResource(iconRes)
        binding.tvTitle.text = title
        if (!message.isNullOrEmpty()) {
            binding.tvMessage.visibility = View.VISIBLE
            binding.tvMessage.text = message
        } else {
            binding.tvMessage.visibility = View.GONE
        }
        okText?.let { binding.btnOk.text = it }
        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            onOk?.invoke()
        }

        // Tie the window to the host lifecycle so a teardown mid-popup can't leak it.
        if (lifecycle != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dialog.isShowing) dialog.dismiss()
                }
            }
            lifecycle.addObserver(observer)
            dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
        }

        dialog.show()
        // Width must be set AFTER show() or the match_parent card collapses to a narrow
        // column (same fix as MapFragment.showInfoPopup).
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    /** Loading/dismiss handle handed to a [confirm] caller: spin the Yes button while its async
     *  action runs, then dismiss on success (or clear on error to let the user retry). */
    interface Loader {
        fun setLoading(loading: Boolean)
        fun dismiss()
    }

    /**
     * Confirmation variant — two buttons (Yes / No). Tapping Yes does NOT auto-dismiss; it hands the
     * caller a [Loader] so it can show an in-button spinner on Yes until its request responds, then
     * dismiss (success) or clear the spinner (error). No dismisses immediately. While loading, the
     * sheet can't be swiped / tapped away.
     */
    fun confirm(
        context: Context,
        title: String,
        message: String? = null,
        @DrawableRes iconRes: Int = R.drawable.ic_error_circle,
        yesText: String,
        noText: String,
        lifecycle: Lifecycle? = null,
        onYes: (Loader) -> Unit,
    ): Dialog {
        val binding = DialogInfoPopupBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        binding.ivIcon.setImageResource(iconRes)
        binding.tvTitle.text = title
        if (!message.isNullOrEmpty()) {
            binding.tvMessage.visibility = View.VISIBLE
            binding.tvMessage.text = message
        } else {
            binding.tvMessage.visibility = View.GONE
        }

        binding.btnOk.text = yesText
        binding.btnCancel.visibility = View.VISIBLE
        binding.btnCancel.text = noText
        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        val loader = object : Loader {
            override fun setLoading(loading: Boolean) {
                binding.btnOk.text = if (loading) "" else yesText
                binding.btnOk.isEnabled = !loading
                binding.btnCancel.isEnabled = !loading
                binding.pbOk.visibility = if (loading) View.VISIBLE else View.GONE
                dialog.setCancelable(!loading)
                dialog.setCanceledOnTouchOutside(!loading)
            }

            override fun dismiss() {
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        binding.btnOk.setOnClickListener { onYes(loader) }

        if (lifecycle != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dialog.isShowing) dialog.dismiss()
                }
            }
            lifecycle.addObserver(observer)
            dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
        }

        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }
}
