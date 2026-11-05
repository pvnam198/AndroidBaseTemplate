package com.namstd.androidskillhub.feature.onboarding

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.config.IntroSetup
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityOnboardingBinding
import com.namstd.androidskillhub.feature.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {
    /**
     * Nội dung của từng vị trí trong pager: index trang nội dung (tính từ 0, đọc `intro{index + 1}_setup`), hoặc
     * [AD_PAGE] cho trang native full-screen mà `native_intro_full` chèn sau một trang nội dung.
     */
    private lateinit var pages: List<Int>
    private var revealJob: Job? = null

    override fun inflateBinding(inflater: LayoutInflater): ActivityOnboardingBinding =
        ActivityOnboardingBinding.inflate(inflater)

    override fun initViews() {
        pages = buildPages(OnboardingPageFragment.CONTENT_PAGE_COUNT, RemoteConfig.nativeIntroFullPages)
        binding.onboardingPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(position: Int) =
                if (pages[position] == AD_PAGE) OnboardingAdFragment() else OnboardingPageFragment.newInstance(pages[position])
        }
        binding.onboardingPager.isUserInputEnabled = RemoteConfig.introSwipe
        binding.pageIndicator.isVisible = false
        // Mỗi trang nội dung một chấm - trang native full-screen không có chấm. Chấm chỉ báo tiến độ nên không bấm được.
        repeat(OnboardingPageFragment.CONTENT_PAGE_COUNT) {
            binding.pageDots.addTab(binding.pageDots.newTab().also { tab -> tab.view.isClickable = false })
        }
        // ViewPager2 báo trang 0 từ bên trong lượt layout của chính nó, lúc đó thay đổi visibility không được layout lại -
        // nên setup của trang đầu được áp trước lần layout đầu tiên. Trang 0 luôn là trang nội dung.
        applySetup(RemoteConfig.introSetup(pages.first() + 1))

        loadBanner(binding.bannerContainer, inApp = false)
    }

    override fun initListeners() {
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val label = if (position == pages.lastIndex) R.string.get_started else R.string.next
                binding.nextButton.setText(label)
                binding.nextText.setText(label)
                if (pages[position] == AD_PAGE) {
                    // Trang quảng cáo native fullscreen có control riêng (xem OnboardingAdFragment) → ẩn nút đáy,
                    // và không có chấm (INVISIBLE, không GONE, để trang không nhảy chiều cao).
                    revealJob?.cancel()
                    binding.nextButton.isVisible = false
                    binding.nextText.isVisible = false
                    if (binding.pageDots.isVisible) binding.pageDots.visibility = View.INVISIBLE
                } else {
                    binding.pageDots.getTabAt(pages[position])?.select()
                    applySetup(RemoteConfig.introSetup(pages[position] + 1))
                }
            }
        })
        binding.nextButton.setOnClickListener { advanceOnboarding() }
        binding.nextText.setOnClickListener { advanceOnboarding() }
    }

    /** Áp `intro{n}_setup` của trang đang hiện: kiểu nút Next, chấm tròn, vị trí cụm chấm, delay trước khi hiện Next. */
    private fun applySetup(setup: IntroSetup) {
        // next: 1 = nút (button), 2 = chữ "Next" không có nền, bấm vào chữ để đi tiếp. Ẩn control không dùng.
        val useText = setup.nextStyle == 2
        val nextControl: View = if (useText) binding.nextText else binding.nextButton
        (if (useText) binding.nextButton else binding.nextText).isVisible = false

        // dot: 1 = chấm tròn, 0 = ẩn.
        binding.pageDots.isVisible = setup.showDots

        // ds: 0 = cụm chấm xuống dưới nút Next, 1 = phía trên.
        val root = binding.root
        val indicatorBelow = root.indexOfChild(binding.indicatorContainer) > root.indexOfChild(binding.nextContainer)
        if (indicatorBelow != (setup.indicatorPosition == 0)) {
            root.removeView(binding.indicatorContainer)
            val nextIndex = root.indexOfChild(binding.nextContainer)
            root.addView(binding.indicatorContainer, if (setup.indicatorPosition == 0) nextIndex + 1 else nextIndex)
        }

        revealJob?.cancel()
        if (setup.delayMs <= 0L) {
            nextControl.isVisible = true
        } else {
            nextControl.isVisible = false
            revealJob = lifecycleScope.launch {
                delay(setup.delayMs)
                nextControl.isVisible = true
            }
        }
    }

    /** Sang trang onboarding kế tiếp, hoặc kết thúc setup ở trang cuối. Public để [OnboardingAdFragment] gọi từ control riêng của nó. */
    fun advanceOnboarding() {
        if (binding.onboardingPager.currentItem < pages.lastIndex) binding.onboardingPager.currentItem += 1 else finishSetup()
    }

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
        revealJob?.cancel()
        binding.onboardingPager.adapter = null
    }

    companion object {
        private const val AD_PAGE = -1

        /**
         * Các trang nội dung từ 0 đến trước [contentCount], có một [AD_PAGE] sau mỗi trang nội dung liệt kê trong
         * [adAfterPages] (tính từ 1). Không bao giờ đặt sau trang nội dung cuối - Onboarding luôn kết thúc ở trang nội dung.
         */
        internal fun buildPages(contentCount: Int, adAfterPages: Set<Int>): List<Int> =
            (0 until contentCount).flatMap { index ->
                if (index + 1 in adAfterPages && index + 1 < contentCount) listOf(index, AD_PAGE) else listOf(index)
            }
    }
}
