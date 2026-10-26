package top.xiaofeigun.tvliveplayer.ui.player

import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton

/**
 * 右侧快捷面板（设置 / 源管理 / 收藏）：负责显隐、遥控器焦点与按键处理。
 * 从覆盖其上的设置等页面返回时自动恢复按钮焦点。
 */
class IconPanel(
    private val panel: View,
    private val buttons: List<ImageButton>,
    private val listener: Listener
) {
    interface Listener {
        fun onIconAction(index: Int)
        fun onClose()
        /** 未聚焦任何按钮时按 OK：恢复播放并关闭面板 */
        fun onResumeAndClose()
    }

    /** 当前聚焦按钮，-1 表示焦点已移出按钮回到视频区域 */
    private var selectedIndex = -1
    private var lastTopPage: Page? = null

    init {
        buttons.forEachIndexed { index, btn ->
            btn.isFocusable = true
            btn.isFocusableInTouchMode = true
            btn.setOnClickListener { listener.onIconAction(index) }
            btn.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) selectedIndex = index }
        }
    }

    fun render(state: PlayerUiState) {
        val top = state.topPage
        when {
            top == Page.ICON -> {
                if (panel.visibility != View.VISIBLE || lastTopPage != Page.ICON) {
                    panel.visibility = View.VISIBLE
                    focus(selectedIndex.coerceAtLeast(0))
                }
            }
            // 设置等页面覆盖在面板之上时保留面板与选中项，返回后恢复焦点
            top.isFragmentPage -> Unit
            else -> {
                panel.visibility = View.GONE
                selectedIndex = -1
            }
        }
        lastTopPage = top
    }

    fun handleKey(keyCode: Int): Boolean {
        val count = buttons.size
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                focus(selectedIndex.coerceAtLeast(0))
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selectedIndex = -1
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                focus(if (selectedIndex >= 0) (selectedIndex - 1 + count) % count else 0)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                focus(if (selectedIndex >= 0) (selectedIndex + 1) % count else 0)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (selectedIndex >= 0) listener.onIconAction(selectedIndex) else listener.onResumeAndClose()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                listener.onClose()
                true
            }
            else -> false
        }
    }

    private fun focus(index: Int) {
        selectedIndex = index
        val target = buttons.getOrNull(index) ?: buttons.first()
        target.post { target.requestFocus() }
    }
}
