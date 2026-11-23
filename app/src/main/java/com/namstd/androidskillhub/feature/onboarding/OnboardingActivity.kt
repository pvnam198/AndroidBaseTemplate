package com.namstd.androidskillhub.feature.onboarding

import android.content.Intent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.OnboardingSetup
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityOnboardingBinding
import com.namstd.androidskillhub.feature.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {
    /**
     * Nội dung của từng vị trí trong pager: index trang nội dung (tính từ 0), hoặc [AD_PAGE] cho trang native
     * full-screen mà `native_full_onboarding` chèn sau một trang nội dung.
     */
    private lateinit var pages: List<Int>
    private var revealJob: Job? = null

    /** Trang nội dung đã hiện nút Next (hết `next_delay_ms`) - quay lại trang đó thì Next hiện ngay, không chờ lại. */
    private val revealedPages = mutableSetOf<Int>()

    /** Nút Next của trang nội dung đang xem đã hiện chưa. */
    private var nextReady = false

    /** Cách sắp xếp cụm điều khiển đang áp (kiểu Next, vị trí chấm) - chỉ dựng lại khi đổi. */
    private var arrangement: Pair<Boolean, Int>? = null

    private var previousPosition = 0

    /** User vừa vuốt lùi - gặp trang native full không có ad thì bỏ qua về trang trước. */
    private var movingBack = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityOnboardingBinding =
        ActivityOnboardingBinding.inflate(inflater)

    override fun initViews() {
        InterstitialAds.preload(InterstitialPlacement.ONBOARDING)
        pages = buildPages(OnboardingPageFragment.CONTENT_PAGE_COUNT, fullNativePages())
        binding.onboardingPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(position: Int) =
                if (pages[position] == AD_PAGE) OnboardingAdFragment() else OnboardingPageFragment.newInstance(pages[position])
        }
        // Mỗi trang nội dung một chấm - trang native full-screen không có chấm. Chấm chỉ báo tiến độ nên không bấm được.
        repeat(OnboardingPageFragment.CONTENT_PAGE_COUNT) {
            binding.pageDots.addTab(binding.pageDots.newTab().also { tab -> tab.view.isClickable = false })
        }
        // ViewPager2 báo trang 0 từ bên trong lượt layout của chính nó, lúc đó thay đổi visibility không được layout lại -
        // nên setup của trang đầu được áp trước lần layout đầu tiên. Trang 0 luôn là trang nội dung.
        applySetup(RemoteConfig.onboardingSetup(pages.first() + 1))
    }

    override fun initListeners() {
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                movingBack = position < previousPosition
                previousPosition = position
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
                    refreshSwipe()
                    if (adPageAt(position)?.adUnavailable == true) skipCurrentAdPage()
                } else {
                    binding.pageDots.getTabAt(pages[position])?.select()
                    applySetup(RemoteConfig.onboardingSetup(pages[position] + 1))
                }
            }
        })
        binding.nextButton.setOnClickListener { advanceOnboarding() }
        binding.nextText.setOnClickListener { advanceOnboarding() }
    }

    /** Áp cấu hình của trang đang hiện ([RemoteConfig.onboardingSetup]): kiểu nút Next, chấm tròn, vị trí cụm chấm, delay trước khi hiện Next. */
    private fun applySetup(setup: OnboardingSetup) {
        // nextStyle: 1 = nút (button), 2 = chữ "Next" không có nền, bấm vào chữ để đi tiếp. Ẩn control không dùng.
        val useText = setup.nextStyle == 2
        val nextControl: View = if (useText) binding.nextText else binding.nextButton
        (if (useText) binding.nextButton else binding.nextText).isVisible = false

        // Chấm tròn báo trang.
        binding.pageDots.isVisible = setup.showDots
        arrangeControls(useText, setup.indicatorPosition)

        revealJob?.cancel()
        val page = pages[binding.onboardingPager.currentItem]
        if (setup.delayMs <= 0L || page in revealedPages) {
            revealNext(nextControl, page)
        } else {
            nextControl.isVisible = false
            nextReady = false
            revealJob = lifecycleScope.launch {
                delay(setup.delayMs)
                revealNext(nextControl, page)
            }
        }
        refreshSwipe()
    }

    private fun revealNext(nextControl: View, page: Int) {
        nextControl.isVisible = true
        revealedPages += page
        nextReady = true
        refreshSwipe()
    }

    /**
     * Sắp xếp chấm + Next trong `controls_area`: text = một hàng (chấm trái, Next phải - chấm tắt thì khung chấm vẫn
     * chiếm phần trái nên Next vẫn bên phải); icon = cột, chấm giữa, trên ([indicatorPosition] 1) hoặc dưới (0) nút.
     */
    private fun arrangeControls(useText: Boolean, indicatorPosition: Int) {
        val key = useText to indicatorPosition
        if (arrangement == key) return
        arrangement = key
        val area = binding.controlsArea
        val dots = binding.indicatorContainer
        val next = binding.nextContainer
        val gap = (8 * resources.displayMetrics.density).toInt()
        area.removeAllViews()
        if (useText) {
            area.orientation = LinearLayout.HORIZONTAL
            area.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            area.addView(dots, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            area.addView(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            (binding.pageDots.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            area.orientation = LinearLayout.VERTICAL
            area.gravity = Gravity.CENTER_HORIZONTAL
            val dotsLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val nextLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            (binding.pageDots.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
            if (indicatorPosition == 0) {
                dotsLp.topMargin = gap
                area.addView(next, nextLp)
                area.addView(dots, dotsLp)
            } else {
                dotsLp.bottomMargin = gap
                area.addView(dots, dotsLp)
                area.addView(next, nextLp)
            }
        }
    }

    /**
     * Vuốt chuyển trang: bật khi `onboarding_swipe` và nút đi tiếp của trang đang xem đã hiện (hết `next_delay_ms`;
     * trang native full: đã hiện X / Next) - vuốt không bỏ qua được thời gian chờ. Trang native full gọi lại khi nút
     * của nó hiện.
     */
    internal fun refreshSwipe() {
        val position = binding.onboardingPager.currentItem
        val canGoNext = if (pages[position] == AD_PAGE) adPageAt(position)?.canGoNext == true else nextReady
        binding.onboardingPager.isUserInputEnabled = RemoteConfig.onboardingSwipe && canGoNext
    }

    /**
     * Trang native full đang hiện mà không có ad: bỏ qua theo hướng user đang đi (vuốt lùi thì về trang trước), không
     * để user thấy trang trống. Trang native full không bao giờ là trang đầu / cuối.
     */
    internal fun skipCurrentAdPage() {
        val position = binding.onboardingPager.currentItem
        if (pages[position] != AD_PAGE) return
        if (movingBack) binding.onboardingPager.setCurrentItem(position - 1, false) else advanceOnboarding()
    }

    /**
     * Sang trang onboarding kế tiếp, hoặc kết thúc setup ở trang cuối. Trang native full kế tiếp mà đã biết không có
     * ad ([OnboardingAdFragment.adUnavailable]) thì nhảy qua luôn. Public để [OnboardingAdFragment] gọi từ control
     * riêng của nó (và khi chính nó không có ad).
     */
    fun advanceOnboarding() {
        var next = binding.onboardingPager.currentItem + 1
        if (next <= pages.lastIndex && pages[next] == AD_PAGE && adPageAt(next)?.adUnavailable == true) next++
        if (next <= pages.lastIndex) binding.onboardingPager.currentItem = next else finishSetup()
    }

    /** Fragment trang native full ở vị trí [position] nếu đã tạo (FragmentStateAdapter gắn tag "f" + itemId). */
    private fun adPageAt(position: Int): OnboardingAdFragment? =
        supportFragmentManager.findFragmentByTag("f$position") as? OnboardingAdFragment

    private fun finishSetup() {
        prefs.setupCompleted = true
        showInterstitial(InterstitialPlacement.ONBOARDING) {
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

        /** Các trang nội dung có trang native full chèn ngay sau (`native_full_onboarding`: bật + `pages`); rỗng = không chèn. */
        fun fullNativePages(): Set<Int> =
            AdPolicy.config(AdKey.NATIVE_FULL_ONBOARDING).let { if (it.enable && AdPolicy.nativeAllowed) it.pages else emptySet() }

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
