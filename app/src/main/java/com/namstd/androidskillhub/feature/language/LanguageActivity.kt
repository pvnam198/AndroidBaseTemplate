package com.namstd.androidskillhub.feature.language

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.language.AppLanguage
import com.namstd.androidskillhub.core.navigation.AppRouter
import com.namstd.androidskillhub.core.navigation.StartDestination
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityLanguageBinding
import com.namstd.androidskillhub.feature.main.MainActivity
import com.namstd.androidskillhub.feature.onboarding.OnboardingActivity
import com.namstd.androidskillhub.feature.onboarding.OnboardingPageFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LanguageActivity : BaseActivity<ActivityLanguageBinding>() {
    private lateinit var selectedLanguage: AppLanguage

    override fun inflateBinding(inflater: LayoutInflater): ActivityLanguageBinding =
        ActivityLanguageBinding.inflate(inflater)

    override fun initConfig(savedInstanceState: Bundle?) {
        selectedLanguage = AppLanguage.fromCode(prefs.selectedLanguage) ?: AppLanguage.default
        OnboardingPageFragment.PLACEMENTS.forEach(NativeAds::preload)
        if (RemoteConfig.nativeIntroFullPages.isNotEmpty()) NativeAds.preload(NativePlacement.ONBOARDING_AD)
    }

    override fun initViews() {
        binding.languageList.layoutManager = LinearLayoutManager(this)
        binding.languageList.adapter =
            LanguageAdapter(AppLanguage.entries, selectedLanguage) { selectedLanguage = it }
        loadNative(binding.nativeContainer, NativePlacement.LANGUAGE)

        val setup = RemoteConfig.languageSetup
        // next: 1 = icon dấu check trên thanh tiêu đề, 2 = nút chữ.
        val nextControl = if (setup.nextStyle == 1) binding.applyIcon else binding.applyButton
        lifecycleScope.launch {
            if (setup.loading) binding.applyLoading.isVisible = true
            delay(RemoteConfig.languageDelayMillis)
            binding.applyLoading.isVisible = false
            nextControl.isVisible = true
        }
    }

    override fun initListeners() {
        val apply = {
            prefs.selectedLanguage = selectedLanguage.code
            prefs.applyLocale(selectedLanguage.code)
            showInterstitial(InterstitialPlacement.LANGUAGE) { if (!isFinishing) goToNext() }
        }
        binding.applyButton.setOnClickListener { apply() }
        binding.applyIcon.setOnClickListener { apply() }
    }

    private fun goToNext() {
        val next = when (AppRouter.afterLanguage(prefs.setupCompleted)) {
            StartDestination.ONBOARDING -> OnboardingActivity::class.java
            StartDestination.HOME -> MainActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }

    override fun releaseResources() {
        binding.languageList.adapter = null
    }
}
