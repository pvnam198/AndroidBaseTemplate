package com.namstd.androidskillhub.feature.main

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ui.base.AdFeedItem
import com.namstd.androidskillhub.core.ui.base.BaseAdRecyclerAdapter

/** Đặt một [AdFeedItem.AdSlot] ở vị trí thứ 3 (index 2, tính từ 0), sau đó cứ mỗi 8 item lại đặt một cái. */
private fun buildMainFeedItems(contentTitles: List<String>): List<AdFeedItem<String>> {
    val result = mutableListOf<AdFeedItem<String>>()
    var nextAdIndex = 2
    for (title in contentTitles) {
        if (result.size == nextAdIndex) {
            result += AdFeedItem.AdSlot
            nextAdIndex += 8
        }
        result += AdFeedItem.Content(title)
    }
    return result
}

class MainFeedAdapter(
    activity: Activity,
    contentTitles: List<String>,
) : BaseAdRecyclerAdapter<String>(activity, buildMainFeedItems(contentTitles), NativePlacement.HOME_FEED) {

    override fun onCreateContentViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_main_content, parent, false) as TextView
        return ContentViewHolder(view)
    }

    override fun onBindContent(holder: RecyclerView.ViewHolder, value: String, position: Int) {
        (holder as ContentViewHolder).bind(value)
    }

    private class ContentViewHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(title: String) {
            text.text = title
        }
    }
}
