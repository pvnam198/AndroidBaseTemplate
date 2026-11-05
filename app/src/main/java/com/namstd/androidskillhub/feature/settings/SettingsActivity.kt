package com.namstd.androidskillhub.feature.settings

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.view.isVisible
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.BannerPlacement
import com.namstd.androidskillhub.core.language.AppLanguage
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivitySettingsBinding
import com.namstd.androidskillhub.feature.debug.DebugConfigActivity
import com.namstd.androidskillhub.feature.premium.PremiumActivity

class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivitySettingsBinding =
        ActivitySettingsBinding.inflate(inflater)

    override fun initViews() {
        binding.privacyButton.isVisible = Ads.isPrivacyOptionsRequired
        binding.debugConfigButton.isVisible = BuildConfig.DEBUG
        loadBanner(binding.bannerContainer, BannerPlacement.SETTING)
    }

    override fun initListeners() {
        fun language(language: AppLanguage) { prefs.selectedLanguage = language.code; prefs.applyLocale(language.code) }
        binding.englishButton.setOnClickListener { language(AppLanguage.ENGLISH) }
        binding.vietnameseButton.setOnClickListener { language(AppLanguage.VIETNAMESE) }
        binding.privacyButton.setOnClickListener { Ads.showPrivacyOptions(this) }
        binding.resetOnboardingButton.setOnClickListener {
            prefs.resetOnboarding()
            Toast.makeText(this, R.string.onboarding_reset, Toast.LENGTH_SHORT).show()
        }
        binding.premiumButton.setOnClickListener { startActivity(Intent(this, PremiumActivity::class.java)) }
        binding.debugConfigButton.setOnClickListener { startActivity(Intent(this, DebugConfigActivity::class.java)) }
    }
}
