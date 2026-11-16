package com.namstd.androidskillhub.feature.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseFragment
import com.namstd.androidskillhub.databinding.FragmentOnboardingAdBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trang onboarding riêng mà toàn bộ nội dung là một native ad. Không có ad (tắt / lỗi / không fill) thì trang tự bỏ
 * qua: đang hiện thì sang trang kế, chưa hiện thì [OnboardingActivity.advanceOnboarding] nhảy qua nó.
 */
class OnboardingAdFragment : BaseFragment<FragmentOnboardingAdBinding>() {
    /** Đã biết chắc trang này không có ad để hiện. */
    var adUnavailable = false
        private set

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentOnboardingAdBinding.inflate(inflater, container, false)

    override fun initViews() {
        loadNative(binding.nativeContainer, NativePlacement.ONBOARDING_AD, onUnavailable = {
            adUnavailable = true
            if (isResumed) skipPage()
        })

        val control: View = if (RemoteConfig.onboardingFullCloseWithX) binding.adClose else binding.adNext
        val waitMs = AdPolicy.config(AdKey.NATIVE_FULL_ONBOARDING).waitMs
        if (waitMs > 0L) {
            binding.adLoading.isVisible = true
            viewLifecycleOwner.lifecycleScope.launch {
                // ViewPager2 tạo sẵn trang này trong lúc trang trước còn trên màn hình (nó ở trạng thái
                // STARTED cho tới khi được chọn), nên chỉ bắt đầu đếm ngược khi trang đã RESUMED.
                viewLifecycleOwner.lifecycle.withResumed {}
                delay(waitMs)
                binding.adLoading.isVisible = false
                control.isVisible = true
            }
        } else {
            control.isVisible = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Vuốt tới trang này khi đã biết không có ad: bỏ qua luôn.
        if (adUnavailable) skipPage()
    }

    private fun skipPage() {
        (activity as? OnboardingActivity)?.advanceOnboarding()
    }

    override fun initListeners() {
        val advance = { (requireActivity() as OnboardingActivity).advanceOnboarding() }
        binding.adNext.setOnClickListener { advance() }
        binding.adClose.setOnClickListener { advance() }
    }
}
