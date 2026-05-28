package com.wangyg.tvliveplayer.ui.browse

import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.wangyg.tvliveplayer.MainActivity
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChannelBrowseFragment : BrowseSupportFragment() {

    private val channelRepository: ChannelRepository
        get() = (activity as MainActivity).channelRepository

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        adapter = rowsAdapter

        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is Channel) {
                (activity as? MainActivity)?.startPlayer(item.name)
            }
        }

        loadChannels()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadChannels() {
        scope.launch {
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
