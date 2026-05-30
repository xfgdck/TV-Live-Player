package com.wangyg.tvliveplayer.ui.channel

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChannelEditFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository

    private var focusArea = FOCUS_CHANNEL
    private var selectedCategory = 0
    private var selectedChannel = 0
    private var categories: List<String> = emptyList()
    private var channels: List<Channel> = emptyList()
    private var channelViews: MutableList<TextView> = mutableListOf()
    private var categoryViews: MutableList<TextView> = mutableListOf()

    private lateinit var categoryContainer: ViewGroup
    private lateinit var channelContainer: ViewGroup

    companion object {
        private const val FOCUS_CATEGORY = 0
        private const val FOCUS_CHANNEL = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_channel_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryContainer = view.findViewById(R.id.category_grid)
        channelContainer = view.findViewById(R.id.channel_grid)

        loadData()

        view.isFocusable = true
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKey(keyCode)
            } else false
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            categories = channelRepository.getAllCategoriesWithFavorite().first()
            rebuildCategoryList()
            if (categories.isNotEmpty()) {
                selectCategory(0)
            }
        }
    }

    private fun rebuildCategoryList() {
        categoryViews.clear()
        categoryContainer.removeAllViews()
        categories.forEachIndexed { index, cat ->
            val tv = TextView(requireContext()).apply {
                text = cat
                textSize = 16f
                setPadding(16, 10, 16, 10)
                isFocusable = true
                isClickable = true
                setOnClickListener { selectCategory(index) }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        focusArea = FOCUS_CATEGORY
                        selectedCategory = index
                        updateHighlights()
                    }
                }
            }
            categoryContainer.addView(tv)
            categoryViews.add(tv)
        }
        updateHighlights()
    }

    private fun selectCategory(index: Int) {
        selectedCategory = index
        selectedChannel = 0
        focusArea = FOCUS_CHANNEL
        lifecycleScope.launch {
            val cat = categories[index]
            channels = channelRepository.getChannelsByCategory(cat).first()
            rebuildChannelList()
            updateHighlights()
            if (channelViews.isNotEmpty()) {
                channelViews[0].requestFocus()
            }
        }
    }

    private fun rebuildChannelList() {
        channelViews.clear()
        channelContainer.removeAllViews()
        channels.forEachIndexed { index, channel ->
            val tv = TextView(requireContext()).apply {
                text = channel.name
                textSize = 16f
                setPadding(16, 10, 16, 10)
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    selectedChannel = index
                    focusArea = FOCUS_CHANNEL
                    updateHighlights()
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        focusArea = FOCUS_CHANNEL
                        selectedChannel = index
                        updateHighlights()
                    }
                }
            }
            channelContainer.addView(tv)
            channelViews.add(tv)
        }
    }

    private fun updateHighlights() {
        categoryViews.forEachIndexed { index, tv ->
            if (index == selectedCategory && focusArea == FOCUS_CATEGORY) {
                tv.setTextColor(resources.getColor(R.color.white, null))
                tv.setBackgroundResource(R.drawable.focus_bg_selected)
            } else if (index == selectedCategory) {
                tv.setTextColor(resources.getColor(R.color.accent, null))
                tv.setBackgroundColor(0x33FFFFFF.toInt())
            } else {
                tv.setTextColor(resources.getColor(R.color.text_secondary, null))
                tv.setBackgroundColor(0)
            }
        }
        channelViews.forEachIndexed { index, tv ->
            if (index == selectedChannel && focusArea == FOCUS_CHANNEL) {
                tv.setTextColor(resources.getColor(R.color.white, null))
                tv.setBackgroundResource(R.drawable.focus_bg_selected)
            } else {
                tv.setTextColor(resources.getColor(R.color.text_secondary, null))
                tv.setBackgroundColor(0)
            }
        }
    }

    private fun handleKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focusArea == FOCUS_CHANNEL) {
                    focusArea = FOCUS_CATEGORY
                    if (selectedCategory < categoryViews.size) {
                        categoryViews[selectedCategory].requestFocus()
                    }
                    updateHighlights()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focusArea == FOCUS_CATEGORY) {
                    focusArea = FOCUS_CHANNEL
                    if (selectedChannel < channelViews.size) {
                        channelViews[selectedChannel].requestFocus()
                    }
                    updateHighlights()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusArea == FOCUS_CATEGORY) {
                    selectedCategory = (selectedCategory - 1 + categories.size) % categories.size
                    selectCategory(selectedCategory)
                } else {
                    selectedChannel = (selectedChannel - 1 + channels.size) % channels.size
                    updateHighlights()
                    if (selectedChannel < channelViews.size) {
                        channelViews[selectedChannel].requestFocus()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusArea == FOCUS_CATEGORY) {
                    selectedCategory = (selectedCategory + 1) % categories.size
                    selectCategory(selectedCategory)
                } else {
                    selectedChannel = (selectedChannel + 1) % channels.size
                    updateHighlights()
                    if (selectedChannel < channelViews.size) {
                        channelViews[selectedChannel].requestFocus()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (focusArea == FOCUS_CHANNEL && channels.isNotEmpty()) {
                    Toast.makeText(requireContext(), "移动模式：按OK确认，返回取消", Toast.LENGTH_SHORT).show()
                }
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ALL_APPS -> {
                if (focusArea == FOCUS_CHANNEL && channels.isNotEmpty()) {
                    showChannelMenu()
                } else if (focusArea == FOCUS_CATEGORY && categories.isNotEmpty()) {
                    showCategoryMenu()
                }
                true
            }
            else -> false
        }
    }

    private fun showChannelMenu() {
        val channel = channels.getOrNull(selectedChannel) ?: return
        val currentCategory = categories[selectedCategory]

        val items = mutableListOf<String>()
        if (currentCategory == ChannelRepository.FAVORITE_CATEGORY) {
            items.add("取消收藏")
        } else {
            items.add("删除")
            items.add("移动到其他分类")
        }

        AlertDialog.Builder(requireActivity())
            .setTitle(channel.name)
            .setItems(items.toTypedArray()) { _, which ->
                if (currentCategory == ChannelRepository.FAVORITE_CATEGORY) {
                    if (which == 0) {
                        lifecycleScope.launch {
                            channelRepository.removeFavorite(channel.id)
                            Toast.makeText(requireContext(), "已取消收藏", Toast.LENGTH_SHORT).show()
                            selectCategory(selectedCategory)
                        }
                    }
                } else {
                    when (which) {
                        0 -> {
                            lifecycleScope.launch {
                                channelRepository.deleteChannel(channel)
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                                selectCategory(selectedCategory)
                            }
                        }
                        1 -> showMoveCategoryDialog(channel)
                    }
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun showMoveCategoryDialog(channel: Channel) {
        val availableCats = categories.filter {
            it != ChannelRepository.FAVORITE_CATEGORY && it != channel.category
        }
        if (availableCats.isEmpty()) {
            Toast.makeText(requireContext(), "无其他分类可移动", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireActivity())
            .setTitle("移动到分类")
            .setItems(availableCats.toTypedArray()) { _, which ->
                if (which in availableCats.indices) {
                    lifecycleScope.launch {
                        channelRepository.updateChannel(channel.copy(category = availableCats[which]))
                        Toast.makeText(requireContext(), "已移动到 ${availableCats[which]}", Toast.LENGTH_SHORT).show()
                        selectCategory(selectedCategory)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCategoryMenu() {
        val cat = categories[selectedCategory]
        if (cat == ChannelRepository.FAVORITE_CATEGORY) {
            Toast.makeText(requireContext(), "系统分类，不可编辑", Toast.LENGTH_SHORT).show()
            return
        }
        val items = arrayOf("添加分类", "重命名", "删除")
        AlertDialog.Builder(requireActivity())
            .setTitle(cat)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddCategoryDialog()
                    1 -> showRenameCategoryDialog(cat)
                    2 -> {
                        lifecycleScope.launch {
                            val catChannels = channelRepository.getChannelsByCategory(cat).first()
                            catChannels.forEach { ch ->
                                channelRepository.updateChannel(ch.copy(category = "未分类"))
                            }
                            Toast.makeText(requireContext(), "分类已删除", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    }
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "输入新分类名称"
        AlertDialog.Builder(requireActivity())
            .setTitle("添加分类")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    Toast.makeText(requireContext(), "分类 \"$name\" 已创建", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameCategoryDialog(oldName: String) {
        val input = android.widget.EditText(requireContext())
        input.setText(oldName)
        AlertDialog.Builder(requireActivity())
            .setTitle("重命名分类")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    lifecycleScope.launch {
                        val catChannels = channelRepository.getChannelsByCategory(oldName).first()
                        catChannels.forEach { ch ->
                            channelRepository.updateChannel(ch.copy(category = newName))
                        }
                        Toast.makeText(requireContext(), "已重命名为 \"$newName\"", Toast.LENGTH_SHORT).show()
                        loadData()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
