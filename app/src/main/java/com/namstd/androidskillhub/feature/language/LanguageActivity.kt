package com.namstd.androidskillhub.feature.language

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.RemoteConfig
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

/**
 * Màn chọn ngôn ngữ, dùng ở hai chỗ:
 * - lần đầu cài app (Splash → Language → Intro): native `NATIVE_LANGUAGE`, bấm Apply có inter `inter_language`, rồi
 *   sang Intro / Home;
 * - mở từ Cài đặt ([openFromSettings]): native `NATIVE_LANGUAGE_2`, bấm Apply thì lưu ngôn ngữ và quay lại Cài đặt,
 *   không có inter.
 * Cả hai dùng chung vị trí `native_language` (`enable` bật/tắt và chọn layout).
 */
class LanguageActivity : BaseActivity<ActivityLanguageBinding>() {
    private lateinit var selectedLanguage: AppLanguage
    private val fromSettings get() = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

    override fun inflateBinding(inflater: LayoutInflater): ActivityLanguageBinding =
        ActivityLanguageBinding.inflate(inflater)

    override fun initConfig(savedInstanceState: Bundle?) {
        selectedLanguage = AppLanguage.fromCode(prefs.selectedLanguage) ?: AppLanguage.default
        if (fromSettings) return
        OnboardingPageFragment.PLACEMENTS.filter(AdPolicy::nativeEnabled).forEach(NativeAds::preload)
        if (AdPolicy.config(AdKey.NATIVE_INTRO_FULL).enableDigits.isNotEmpty()) NativeAds.preload(NativePlacement.ONBOARDING_AD)
    }

    override fun initViews() {
        binding.languageList.layoutManager = LinearLayoutManager(this)
        binding.languageList.adapter =
            LanguageAdapter(AppLanguage.entries, selectedLanguage) { selectedLanguage = it }
        loadNative(binding.nativeContainer, if (fromSettings) NativePlacement.LANGUAGE_SETTING else NativePlacement.LANGUAGE)

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
            if (fromSettings) finish() else showInterstitial(InterstitialPlacement.LANGUAGE) { if (!isFinishing) goToNext() }
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

    companion object {
        private const val EXTRA_FROM_SETTINGS = "from_settings"

        /** Mở màn chọn ngôn ngữ từ Cài đặt (native `NATIVE_LANGUAGE_2`, Apply xong quay lại). */
        fun openFromSettings(context: Context) =
            context.startActivity(Intent(context, LanguageActivity::class.java).putExtra(EXTRA_FROM_SETTINGS, true))
    }
}
