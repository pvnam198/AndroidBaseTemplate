package com.namstd.androidskillhub.feature.onboarding

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.config.Intro1Setup
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityOnboardingBinding
import com.namstd.androidskillhub.feature.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {
    private lateinit var setup: Intro1Setup
    private lateinit var nextControl: View
    private var dotsMediator: TabLayoutMediator? = null

    override fun inflateBinding(inflater: LayoutInflater): ActivityOnboardingBinding =
        ActivityOnboardingBinding.inflate(inflater)

    override fun initViews() {
        binding.onboardingPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = PAGE_COUNT
            override fun createFragment(position: Int) =
                if (position == AD_PAGE) OnboardingAdFragment() else OnboardingPageFragment.newInstance(contentIndex(position))
        }
        binding.onboardingPager.isUserInputEnabled = RemoteConfig.introSwipe

        setup = RemoteConfig.intro1Setup

        // next: 1 = nút chữ, 2 = icon. Ẩn control không dùng.
        val useIcon = setup.nextStyle == 2
        nextControl = if (useIcon) binding.nextIcon else binding.nextButton
        binding.nextButton.isVisible = !useIcon
        binding.nextIcon.isVisible = useIcon
        // Có delay thì ẩn trước, onPageSelected sẽ hiện lại sau delay.
        if (setup.delayMs > 0L) nextControl.isVisible = false

        // dot: 1 = chấm tròn (TabLayout), 0 = text "x/n".
        if (setup.showDots) {
            binding.pageIndicator.isVisible = false
            binding.pageDots.isVisible = true
            dotsMediator = TabLayoutMediator(binding.pageDots, binding.onboardingPager) { _, _ -> }
                .also { it.attach() }
        } else {
            binding.pageDots.isVisible = false
            binding.pageIndicator.isVisible = true
        }

        // ds: 0 = indicator xuống dưới nút Next (mặc định 1 = phía trên).
        if (setup.indicatorPosition == 0) {
            val root = binding.root
            root.removeView(binding.indicatorContainer)
            root.addView(binding.indicatorContainer, root.indexOfChild(binding.nextContainer) + 1)
        }

        loadBanner(binding.bannerContainer, inApp = false)
    }

    override fun initListeners() {
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.pageIndicator.text = "${position + 1} / $PAGE_COUNT"
                binding.nextButton.setText(if (position == PAGE_COUNT - 1) R.string.get_started else R.string.next)
                // Trang quảng cáo native fullscreen có control riêng (xem OnboardingAdFragment) → ẩn nút đáy.
                if (position == AD_PAGE) nextControl.isVisible = false else revealNextAfterDelay()
            }
        })
        binding.nextButton.setOnClickListener { advanceOnboarding() }
        binding.nextIcon.setOnClickListener { advanceOnboarding() }
    }

    /** Sang trang onboarding kế tiếp, hoặc kết thúc setup ở trang cuối. Public để [OnboardingAdFragment] gọi từ control riêng của nó. */
    fun advanceOnboarding() {
        if (binding.onboardingPager.currentItem < PAGE_COUNT - 1) binding.onboardingPager.currentItem += 1 else finishSetup()
    }

    /** Hiện nút Next sau [Intro1Setup.delayMs] (ẩn trước nếu có delay). */
    private fun revealNextAfterDelay() {
        if (setup.delayMs <= 0L) { nextControl.isVisible = true; return }
        nextControl.isVisible = false
        lifecycleScope.launch {
            delay(setup.delayMs)
            nextControl.isVisible = true
        }
    }

    /** Maps a page position to its content index, skipping the full-ad page at [AD_PAGE]. */
    private fun contentIndex(position: Int) = if (position > AD_PAGE) position - 1 else position

    private fun finishSetup() {
        prefs.setupCompleted = true
        showInterstitial(InterstitialPlacement.INTRO) {
            if (!isFinishing) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun releaseResources() {
        dotsMediator?.detach()
        dotsMediator = null
        binding.onboardingPager.adapter = null
    }

    companion object {
        private const val PAGE_COUNT = 4
        private const val AD_PAGE = 2
    }
}
