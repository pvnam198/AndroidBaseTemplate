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

/** Trang onboarding riêng mà toàn bộ nội dung là một native ad. */
class OnboardingAdFragment : BaseFragment<FragmentOnboardingAdBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentOnboardingAdBinding.inflate(inflater, container, false)

    override fun initViews() {
        loadNative(binding.nativeContainer, NativePlacement.ONBOARDING_AD)

        val control: View = if (RemoteConfig.nativeIntroUseIconNext) binding.adClose else binding.adNext
        val waitMs = AdPolicy.config(AdKey.NATIVE_INTRO_FULL).waitTimeMs
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

    override fun initListeners() {
        val advance = { (requireActivity() as OnboardingActivity).advanceOnboarding() }
        binding.adNext.setOnClickListener { advance() }
        binding.adClose.setOnClickListener { advance() }
    }
}
