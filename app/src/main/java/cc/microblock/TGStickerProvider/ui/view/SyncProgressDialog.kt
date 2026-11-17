package cc.microblock.TGStickerProvider.ui.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView

/** Material progress UI. Worker callbacks may update/dismiss it safely on the main thread. */
class SyncProgressDialog(context: Context) {
    private var destroyed = false
    private val main = Handler(Looper.getMainLooper())
    private val message = MaterialTextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val spacing = (24 * resources.displayMetrics.density).toInt()
        setPadding(spacing, spacing, spacing, spacing)
        addView(CircularProgressIndicator(context).apply { isIndeterminate = true },
            LinearLayout.LayoutParams(spacing * 2, spacing * 2).apply { marginEnd = spacing })
        addView(message, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    private val dialog = MaterialAlertDialogBuilder(context).setView(content).create()

    private val owner = context as? LifecycleOwner
    private val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            destroyed = true
            main.removeCallbacksAndMessages(null)
            dialog.dismiss()
            owner.lifecycle.removeObserver(this)
        }
    }

    init {
        // Covers both explicit completion and user cancellation of a cancelable phase.
        dialog.setOnDismissListener { owner?.lifecycle?.removeObserver(observer) }
    }

    fun setMessage(text: CharSequence) = onMain { message.text = text }
    fun setCancelable(cancelable: Boolean) = onMain { dialog.setCancelable(cancelable) }
    fun show() = onMain {
        owner?.lifecycle?.addObserver(observer)
        dialog.show()
    }
    fun dismiss() = onMain {
        dialog.dismiss()
        owner?.lifecycle?.removeObserver(observer)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!destroyed && owner?.lifecycle?.currentState != Lifecycle.State.DESTROYED) action()
        } else main.post { if (!destroyed && owner?.lifecycle?.currentState != Lifecycle.State.DESTROYED) action() }
    }
}
