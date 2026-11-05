package com.namstd.androidskillhub.feature.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.AppOpenAds
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.resolveFullScreenPlan
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityMainBinding
import com.namstd.androidskillhub.feature.settings.SettingsActivity

class MainActivity : BaseActivity<ActivityMainBinding>() {

    /** Lần onResume đầu tiên là lúc mở màn hình, khi banner đã đang load - chỉ các lần sau mới reload. */
    private var hasResumedOnce = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun initConfig(savedInstanceState: Bundle?) {
        NativeAds.preload(NativePlacement.HOME_FEED)
    }

    override fun initViews() {
        // Chỉ hâm nóng App Open ad khi việc quay lại app có thể hiện nó (enable.onresume=1).
        if (AdPolicy.enable.onresume == 1) AppOpenAds.load()
        val inAppPlan = resolveFullScreenPlan(InterstitialPlacement.IN_APP, AdPolicy.enable)
        if (inAppPlan == FullScreenAdPlan.INTERSTITIAL || inAppPlan == FullScreenAdPlan.INTER_PLUS_NATIVE) {
            InterstitialAds.load()
        }
        // Main tự reload slot banner - khi resume và khi đổi tab - thay vì theo bộ hẹn giờ.
        loadBanner(binding.bannerContainer, autoReload = false)
        binding.mainFeedPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = TAB_COUNT
            override fun createFragment(position: Int) = MainFeedPageFragment.newInstance(position)
        }
        TabLayoutMediator(binding.mainFeedTabs, binding.mainFeedPager) { tab, position ->
            tab.text = "Tab ${position + 1}"
        }.attach()
    }

    override fun initListeners() {
        binding.mainFeedPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var lastPosition = binding.mainFeedPager.currentItem

            override fun onPageSelected(position: Int) {
                // ViewPager2 cũng gọi hàm này cho trang ban đầu (và khi restore) - chỉ khi đổi tab thật mới reload.
                if (position == lastPosition) return
                lastPosition = position
                reloadBanner()
            }
        })
        binding.ivSetting.setOnClickListener {
            showInterstitial(InterstitialPlacement.IN_APP) {
                if (!isFinishing) startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) reloadBanner() else hasResumedOnce = true
    }

    override fun releaseResources() {
        binding.mainFeedPager.adapter = null
    }

    private companion object {
        const val TAB_COUNT = 3
    }
}
