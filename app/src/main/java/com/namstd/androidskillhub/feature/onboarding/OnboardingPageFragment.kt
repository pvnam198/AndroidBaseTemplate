package com.namstd.androidskillhub.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ui.base.BaseFragment
import com.namstd.androidskillhub.databinding.FragmentOnboardingPageBinding

class OnboardingPageFragment : BaseFragment<FragmentOnboardingPageBinding>() {
    /** Index trang nội dung, 0-3 - không tính trang native full-screen. */
    private val page get() = requireArguments().getInt(ARG_PAGE)

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentOnboardingPageBinding.inflate(inflater, container, false)

    override fun initViews() {
        binding.pageTitle.setText(TITLES[page])
        binding.pageBody.setText(BODIES[page])
        loadNative(binding.nativeContainer, PLACEMENTS[page])
    }

    companion object {
        private const val ARG_PAGE = "page"
        private val TITLES = intArrayOf(
            R.string.onboarding_title_1, R.string.onboarding_title_2, R.string.onboarding_title_3, R.string.onboarding_title_4,
        )
        private val BODIES = intArrayOf(
            R.string.onboarding_body_1, R.string.onboarding_body_2, R.string.onboarding_body_3, R.string.onboarding_body_4,
        )
        val PLACEMENTS = listOf(
            NativePlacement.ONBOARDING_1, NativePlacement.ONBOARDING_2, NativePlacement.ONBOARDING_3, NativePlacement.ONBOARDING_4,
        )

        /** Số trang nội dung - mỗi trang đọc `intro{n}_setup` riêng của nó. */
        val CONTENT_PAGE_COUNT get() = TITLES.size
        fun newInstance(page: Int) = OnboardingPageFragment().apply { arguments = Bundle().apply { putInt(ARG_PAGE, page) } }
    }
}
