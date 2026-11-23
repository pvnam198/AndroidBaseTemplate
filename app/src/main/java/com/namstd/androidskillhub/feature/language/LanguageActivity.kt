package com.namstd.androidskillhub.feature.language

import android.content.Context
import android.content.Intent
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.InterstitialAds
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
 * - lần đầu cài app (Splash → Language → Onboarding): native `NATIVE_LANGUAGE`; nút đi tiếp hiện sau khi chọn ngôn ngữ;
 *   bấm Apply có inter `inter_language` (trước đó dialog loading nhỏ ~1 s nếu inter đã sẵn), rồi sang Onboarding / Home;
 * - mở từ Cài đặt ([openFromSettings]): native `NATIVE_LANGUAGE_2`, bấm Apply thì lưu ngôn ngữ và quay lại Cài đặt,
 *   không có inter.
 * Cả hai dùng chung vị trí `native_language` (`enable` bật/tắt và chọn layout).
 */
class LanguageActivity : BaseActivity<ActivityLanguageBinding>() {
    private lateinit var selectedLanguage: AppLanguage
    private val fromSettings get() = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
    private var nextRevealed = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityLanguageBinding =
        ActivityLanguageBinding.inflate(inflater)

    override fun initConfig(savedInstanceState: Bundle?) {
        selectedLanguage = AppLanguage.fromCode(prefs.selectedLanguage) ?: AppLanguage.default
        if (fromSettings) return
        OnboardingPageFragment.PLACEMENTS.filter(AdPolicy::nativeEnabled).forEach(NativeAds::preload)
        if (OnboardingActivity.fullNativePages().isNotEmpty()) NativeAds.preload(NativePlacement.ONBOARDING_AD)
    }

    override fun initViews() {
        binding.languageList.layoutManager = LinearLayoutManager(this)
        binding.languageList.adapter =
            LanguageAdapter(AppLanguage.entries, selectedLanguage, ::onLanguageSelected)
        loadNative(binding.nativeSmallContainer, NativePlacement.LANGUAGE_SMALL)
        loadNative(binding.nativeContainer, if (fromSettings) NativePlacement.LANGUAGE_SETTING else NativePlacement.LANGUAGE)

        // Lần đầu: chưa chọn ngôn ngữ thì chưa có nút đi tiếp (hiện khi chọn - xem revealNext). Từ Cài đặt: hiện luôn.
        if (fromSettings) revealNext()
    }

    override fun initListeners() {
        val apply = {
            prefs.selectedLanguage = selectedLanguage.code
            if (fromSettings) {
                prefs.applyLocale(selectedLanguage.code)
                finish()
            } else {
                showLanguageInterstitial()
            }
        }
        binding.applyButton.setOnClickListener { apply() }
        binding.applyIcon.setOnClickListener { apply() }
    }

    private fun onLanguageSelected(language: AppLanguage) {
        selectedLanguage = language
        if (!fromSettings && !nextRevealed) revealNext()
    }

    /**
     * Hiện nút đi tiếp theo `settings`: `language_next_style` (icon ✓ trên thanh tiêu đề / nút chữ), sau
     * `language_next_delay_ms` (0 = hiện ngay, không loading); trong lúc chờ có vòng loading ở chỗ nút nếu
     * `language_show_loading`.
     */
    private fun revealNext() {
        nextRevealed = true
        val setup = RemoteConfig.languageSetup
        val icon = setup.nextStyle == 1
        val nextControl = if (icon) binding.applyIcon else binding.applyButton
        val wait = RemoteConfig.languageDelayMillis
        if (wait <= 0L) {
            nextControl.isVisible = true
            return
        }
        val loading = if (icon) binding.applyLoading else binding.applyButtonLoading
        lifecycleScope.launch {
            if (setup.loading) loading.isVisible = true
            delay(wait)
            loading.isVisible = false
            nextControl.isVisible = true
        }
    }

    /**
     * Inter `inter_language` khi lưu ngôn ngữ lần đầu. Có inter sẵn thì trước đó hiện dialog loading nhỏ (chỉ vòng xoay)
     * [LOADING_BEFORE_INTER_MS] để user không thấy quảng cáo bật lên ngay khi bấm; không có thì đi tiếp luôn.
     */
    private fun showLanguageInterstitial() {
        // Chặn bấm lần 2 trong lúc loading / inter.
        binding.applyButton.isEnabled = false
        binding.applyIcon.isEnabled = false
        val showAd = { showInterstitial(InterstitialPlacement.LANGUAGE) { if (!isFinishing) goToNext() } }
        if (isPremiumUser() || !InterstitialAds.isReady(InterstitialPlacement.LANGUAGE.interUnit)) {
            showAd()
            return
        }
        val loading = Dialog(this).apply {
            setContentView(R.layout.dialog_simple_loading)
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            delay(LOADING_BEFORE_INTER_MS)
            loading.dismiss()
            if (!isFinishing && !isDestroyed) showAd()
        }
    }

    /**
     * Lần đầu: đổi locale ngay trước khi sang màn sau (không đổi lúc bấm Apply - đổi locale làm Activity này bị tạo lại,
     * mất dialog loading / luồng inter đang chạy).
     */
    private fun goToNext() {
        prefs.applyLocale(selectedLanguage.code)
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
        private const val LOADING_BEFORE_INTER_MS = 1_000L

        /** Mở màn chọn ngôn ngữ từ Cài đặt (native `NATIVE_LANGUAGE_2`, Apply xong quay lại). */
        fun openFromSettings(context: Context) =
            context.startActivity(Intent(context, LanguageActivity::class.java).putExtra(EXTRA_FROM_SETTINGS, true))
    }
}
