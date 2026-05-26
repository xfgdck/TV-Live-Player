package com.wangyg.tvliveplayer.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import coil3.load
import coil3.request.crossfade
import coil3.request.error

class ChannelPresenter : Presenter() {
    class ViewHolder(view: ViewGroup) : Presenter.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_channel_name)
        val ivLogo: ImageView = view.findViewById(R.id.iv_channel_logo)
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_card, parent, false) as ViewGroup
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val channel = item as? Channel ?: return
        val holder = viewHolder as ViewHolder
        holder.tvName.text = channel.name
        if (!channel.logo.isNullOrEmpty()) {
            holder.ivLogo.load(channel.logo) {
                crossfade(true)
                error(R.drawable.ic_tv)
            }
        } else {
            holder.ivLogo.setImageResource(R.drawable.ic_tv)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
    }
}
