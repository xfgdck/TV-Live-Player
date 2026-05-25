package com.wangyg.tvliveplayer.ui.browse

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseFragment as LeanbackBrowseFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BrowseFragment : LeanbackBrowseFragment() {

    @Inject lateinit var channelRepository: ChannelRepository

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        adapter = rowsAdapter

        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is Channel) {
                val intent = Intent(requireContext(), PlayerActivity::class.java)
                intent.putExtra("channel_id", item.id)
                startActivity(intent)
            }
        }

        loadChannels()
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            channelRepository.getCategories().collect { categories ->
                if (categories.isEmpty()) return@collect
                rowsAdapter.clear()

                categories.forEach { category ->
                    val listRow = createCategoryRow(category)
                    listRow?.let { rowsAdapter.add(it) }
                }
            }
        }
    }

    private suspend fun createCategoryRow(category: String): ListRow? {
        val channels = channelRepository.getChannelsByCategory(category).first()
        if (channels.isEmpty()) return null

        val adapter = ArrayObjectAdapter(ChannelPresenter())
        channels.forEach { channel ->
            adapter.add(channel)
        }

        val header = HeaderItem(category)
        return ListRow(header, adapter)
    }
}
