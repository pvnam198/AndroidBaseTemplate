package com.namstd.androidskillhub.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdFormat
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.template.NativeAdTemplate
import com.namstd.androidskillhub.core.ads.template.NativeTemplateSpec
import com.namstd.androidskillhub.core.config.RemoteConfig

/**
 * [DEBUG] Xem trước từng mẫu native với style của `ad_templates` (Firebase hoặc override ở Debug Config), dùng một
 * native test ad, bấm ◀ ▶ để chuyển. Thứ tự: các mẫu tự đặt trong `ad_templates`, rồi các mẫu có sẵn; ngay sau mỗi
 * mẫu Collap* là dải thu gọn của bộ đó (Tiny1 / Tiny2).
 *
 * ```
 * adb shell am start -n com.namstd.androidskillhub/.debug.NativePreviewActivity [--es name big5]
 * ```
 */
class NativePreviewActivity : Activity() {
    private class Page(val label: String, val spec: NativeTemplateSpec)

    private lateinit var title: TextView
    private lateinit var slot: FrameLayout
    private var ad: NativeAd? = null
    private var pages: List<Page> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        title = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            text = "Đang tải native test…"
        }
        slot = FrameLayout(this).apply { setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0) }
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(Button(context).apply { text = "◀"; setOnClickListener { show(index - 1) } })
            addView(Button(context).apply { text = "▶"; setOnClickListener { show(index + 1) } })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEFF3.toInt())
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(slot, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(nav, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })

        val templates = RemoteConfig.templates()
        pages = (templates.customNames + NativeAdTemplate.entries.map { it.key }).flatMap { name ->
            val spec = templates.resolve(name, NativeAdTemplate.BIG1)
            listOfNotNull(
                Page(name, spec),
                spec.template.collapsed?.let { Page("$name · thu gọn", NativeTemplateSpec(it, spec.style)) },
            )
        }
        intent.getStringExtra("name")?.lowercase()?.let { wanted ->
            index = pages.indexOfFirst { it.label == wanted }.coerceAtLeast(0)
        }

        Ads.initialize(this) { ok ->
            if (!ok) { title.text = "Ads chưa sẵn sàng (consent?)"; return@initialize }
            val request = NativeAdRequest.Builder(AdFormat.NATIVE.testId, listOf(NativeAd.NativeAdType.NATIVE)).build()
            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) = runOnUiThread { ad = nativeAd; show(index) }
                override fun onAdFailedToLoad(adError: LoadAdError) = runOnUiThread { title.text = "Lỗi: ${adError.message}" }
            })
        }
    }

    private fun show(i: Int) {
        val ad = ad ?: return
        index = (i + pages.size) % pages.size
        val page = pages[index]
        val spec = page.spec
        val target = if (page.label == spec.template.key) "" else " → ${spec.template.key}"
        val styled = if (spec.style.isEmpty) "" else " · có style"
        title.text = "${index + 1}/${pages.size}  ${page.label}$target$styled"
        val view = NativeAds.renderTemplate(this, ad, spec)
        // Nút thu gọn / đóng / Next của mẫu Collap* và Full* bị ẩn sẵn (màn chạy thật quyết định khi nào hiện) - preview
        // hiện luôn để nhìn giống Figma.
        listOf(R.id.ad_collapse, R.id.ad_close, R.id.ad_next).forEach { id -> view.findViewById<View>(id)?.visibility = View.VISIBLE }
        val full = spec.template.key.startsWith("full")
        slot.removeAllViews()
        slot.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, if (full) MATCH_PARENT else WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onDestroy() {
        ad?.destroy()
        super.onDestroy()
    }
}
