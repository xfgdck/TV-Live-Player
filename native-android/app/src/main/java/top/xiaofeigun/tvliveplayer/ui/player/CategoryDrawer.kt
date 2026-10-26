package top.xiaofeigun.tvliveplayer.ui.player

import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.domain.model.Channel

/**
 * 底部选台抽屉（分类标签 + 频道列表）。
 * 子 View 按需复用，仅在数据变化时重新绑定文字与样式，避免大分类切换时整表重建造成卡顿。
 */
class CategoryDrawer(
    private val overlay: View,
    private val tabsScroll: HorizontalScrollView,
    private val tabs: LinearLayout,
    private val channelScroll: ScrollView,
    private val channelList: LinearLayout,
    private val listener: Listener
) {
    interface Listener {
        fun onCategoryClick(index: Int)
        fun onChannelFocused(index: Int)
        fun onChannelClick(index: Int)
        fun onChannelLongClick(index: Int)
    }

    private val context = overlay.context
    private val colorSelected = ContextCompat.getColor(context, R.color.white)
    private val colorNormal = ContextCompat.getColor(context, R.color.text_secondary)

    private var renderedCategories: List<String>? = null
    private var renderedCategoryIndex = -1
    private var renderedChannels: List<Channel>? = null
    private var renderedChannelIndex = -1
    private var visible = false

    init {
        channelScroll.layoutParams.height = (context.resources.displayMetrics.heightPixels * 0.45).toInt()
    }

    fun render(state: PlayerUiState) {
        val shouldShow = state.topPage == Page.CATEGORY
        overlay.visibility = if (shouldShow) View.VISIBLE else View.GONE
        val justOpened = shouldShow && !visible
        visible = shouldShow
        if (!shouldShow) return

        if (state.allCategories != renderedCategories || state.selectedCategoryIndex != renderedCategoryIndex) {
            bindTabs(state.allCategories, state.selectedCategoryIndex)
        }

        val channelsChanged = state.categoryChannels !== renderedChannels && state.categoryChannels != renderedChannels
        val selectionChanged = state.selectedChannelIndex != renderedChannelIndex
        if (channelsChanged) {
            bindChannels(state.categoryChannels, state.selectedChannelIndex)
        } else if (selectionChanged) {
            styleChannel(renderedChannelIndex, false)
            styleChannel(state.selectedChannelIndex, true)
        }
        renderedChannelIndex = state.selectedChannelIndex
        if (channelsChanged || selectionChanged || justOpened) {
            revealSelectedChannel(state.selectedChannelIndex)
        }
    }

    private fun bindTabs(categories: List<String>, selectedIndex: Int) {
        ensureChildCount(tabs, categories.size) {
            TextView(context).apply {
                textSize = 15f
                setPadding(24, 10, 24, 10)
                isClickable = true
                setOnClickListener { listener.onCategoryClick(tag as Int) }
            }
        }
        categories.forEachIndexed { index, cat ->
            (tabs.getChildAt(index) as TextView).apply {
                tag = index
                text = cat
                val selected = index == selectedIndex
                setTextColor(if (selected) colorSelected else colorNormal)
                setBackgroundResource(if (selected) R.drawable.tab_active_bg else R.drawable.tab_inactive_bg)
            }
        }
        renderedCategories = categories
        renderedCategoryIndex = selectedIndex

        tabsScroll.post {
            val selectedTab = tabs.getChildAt(selectedIndex) ?: return@post
            val scrollX = selectedTab.left - (tabsScroll.width - selectedTab.width) / 2
            tabsScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun bindChannels(channels: List<Channel>, selectedIndex: Int) {
        ensureChildCount(channelList, channels.size) {
            TextView(context).apply {
                textSize = 18f
                setPadding(24, 14, 24, 14)
                minHeight = 48
                isFocusable = true
                isClickable = true
                setOnClickListener { listener.onChannelClick(tag as Int) }
                setOnLongClickListener {
                    listener.onChannelLongClick(tag as Int)
                    true
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) listener.onChannelFocused(v.tag as Int)
                }
            }
        }
        channels.forEachIndexed { index, channel ->
            (channelList.getChildAt(index) as TextView).apply {
                tag = index
                text = channel.name
            }
            styleChannel(index, index == selectedIndex)
        }
        renderedChannels = channels
    }

    private fun styleChannel(index: Int, selected: Boolean) {
        val item = channelList.getChildAt(index) as? TextView ?: return
        item.setTextColor(if (selected) colorSelected else colorNormal)
        item.setBackgroundResource(if (selected) R.drawable.channel_item_focused else R.drawable.channel_item_normal)
    }

    private fun revealSelectedChannel(index: Int) {
        channelScroll.post {
            val focused = channelList.getChildAt(index) ?: return@post
            val scrollY = channelScroll.scrollY
            val viewHeight = channelScroll.height
            if (focused.top < scrollY) {
                channelScroll.scrollTo(0, focused.top)
            } else if (focused.bottom > scrollY + viewHeight) {
                channelScroll.scrollTo(0, focused.bottom - viewHeight)
            }
            focused.requestFocus()
        }
    }

    private inline fun ensureChildCount(parent: LinearLayout, count: Int, create: () -> View) {
        while (parent.childCount < count) parent.addView(create())
        while (parent.childCount > count) parent.removeViewAt(parent.childCount - 1)
    }
}
