package cc.microblock.TGStickerProvider.ui.activity.base

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import cc.microblock.TGStickerProvider.utils.factory.isNotSystemInDarkMode

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    lateinit var binding: VB
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        // Android 15+ enforces edge-to-edge for new targets. Also handle older supported devices.
        val root = binding.root
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = isNotSystemInDarkMode
            isAppearanceLightNavigationBars = isNotSystemInDarkMode
        }
        @Suppress("DEPRECATION")
        window.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            navigationBarDividerColor = Color.TRANSPARENT
        }
        onCreate()
    }

    protected abstract fun inflateBinding(inflater: LayoutInflater): VB
    abstract fun onCreate()
}
