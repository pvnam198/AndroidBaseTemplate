package com.namstd.androidskillhub.feature.ads

import android.graphics.Rect
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.FullScreenGate
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ui.base.BaseDialogFragment
import com.namstd.androidskillhub.databinding.DialogNativeFullBinding
import com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdViewModel.UiState.AwaitingClose
import com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdViewModel.UiState.AwaitingNext
import com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdViewModel.UiState.Countdown
import com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdViewModel.UiState.Progress
import kotlin.math.ceil
import kotlinx.coroutines.launch

/**
 * Nửa sau của "custom interstitial": [totalStages] native ad hiện lần lượt từng cái,
 * mỗi cái cần user bấm rõ ràng để đi tiếp sau khi đếm ngược [durationMs] kết thúc. Hiện dưới dạng
 * full-screen dialog (thay vì một Activity riêng) đè lên màn hình hiện tại để nó xuất hiện
 * ngay lập tức, ngay sau khi interstitial đóng, không có độ trễ chuyển window. Giữ
 * [FullScreenGate] suốt vòng đời của nó để không gì khác (vd. app-open ad khi resume lại
 * foreground) hiện đè lên được, và báo hoàn tất về qua Fragment Result API.
 */
class PostInterstitialNativeAdDialogFragment : BaseDialogFragment<DialogNativeFullBinding>() {

    private val viewModel: PostInterstitialNativeAdViewModel by viewModels()

    /** Vị trí inter đã mở break này - quyết định ad unit native (`native_inter_*`) và `wait_ms`. */
    private val placement: InterstitialPlacement by lazy {
        InterstitialPlacement.valueOf(requireArguments().getString(ARG_PLACEMENT) ?: InterstitialPlacement.SPLASH.name)
    }

    /** Sheet có một native sau mỗi inter (`NATIVE_INTER_*`), nên break chỉ có một stage. */
    private val totalStages: Int = 1
    private val durationMs: Long by lazy { AdPolicy.config(placement.adKey).waitMs }

    private var currentAd: NativeAd? = null
    private var countdownTimer: CountDownTimer? = null
    private var gateHeld = false
    private var currentStage = 0

    companion object {
        const val TAG = "PostInterstitialNativeAdDialog"
        const val REQUEST_KEY = "post_interstitial_native_ad_done"
        private const val ARG_PLACEMENT = "placement"

        fun newInstance(placement: InterstitialPlacement) = PostInterstitialNativeAdDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_PLACEMENT, placement.name) }
        }
        private const val TICK_MS = 50L
    }

    override fun getTheme(): Int = R.style.Theme_AndroidSkillHub_FullScreenDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): DialogNativeFullBinding =
        DialogNativeFullBinding.inflate(inflater, container, false)

    override fun initViews() {
        applySystemBarPadding(binding.root)
        if (!Ads.isReady || !FullScreenGate.acquire(this)) {
            finishBreak(); return
        }
        gateHeld = true
        loadStage(1)
    }

    override fun initListeners() {
        binding.ivNext.setOnClickListener {
            binding.ivNext.setOnClickListener(null)
            releaseCurrentAd()
            loadStage(currentStage + 1)
        }
        binding.ivClose.setOnClickListener {
            binding.ivClose.setOnClickListener(null)
            releaseCurrentAd()
            finishBreak()
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: PostInterstitialNativeAdViewModel.UiState) {
        binding.progressBar.visibility = toVisibility(state is Progress || state is AwaitingNext)
        binding.ivNext.visibility = toVisibility(state is AwaitingNext)
        binding.tvCountdown.visibility = toVisibility(state is Countdown)
        binding.ivClose.visibility = toVisibility(state is AwaitingClose)
        when (state) {
            is Progress -> binding.progressBar.progress = state.percent
            AwaitingNext -> binding.progressBar.progress = 100
            is Countdown -> binding.tvCountdown.text = state.secondsLeft.toString()
            AwaitingClose -> Unit
        }
    }

    private fun toVisibility(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    /** Xử lý insets edge-to-edge giống [com.namstd.androidskillhub.core.ui.base.BaseActivity] tự áp dụng; full-screen dialog cũng cần. */
    private fun applySystemBarPadding(root: View) {
        val initialPadding =
            Rect(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialPadding.left + bars.left,
                top = initialPadding.top + bars.top,
                right = initialPadding.right + bars.right,
                bottom = initialPadding.bottom + bars.bottom,
            )
            insets
        }
    }

    /**
     * Lấy ad đã preload cho stage này - không chờ load. Ad #1 được [BaseActivity.showInterstitial]
     * hâm nóng sẵn trước cả interstitial; mỗi stage sau chỉ bắt đầu preload khi stage trước
     * đã hiện (xem [onStageLoaded]), tận dụng thời gian user xem stage đó. Mọi stage dùng chung
     * slot native sau inter của [placement] vì chúng không bao giờ cần cùng lúc - một cái
     * luôn được dùng hết trước khi cái kế tiếp bắt đầu load. Nếu không có ad nào sẵn kịp
     * thì chuyển sang stage kế tiếp (hoặc đóng, khi đã quá [totalStages]).
     */
    private fun loadStage(stageNumber: Int) {
        val ad = NativeAds.poll(placement.nativePlacement)
        if (ad == null) {
            if (stageNumber < totalStages) loadStage(stageNumber + 1) else finishBreak()
        } else {
            onStageLoaded(stageNumber, ad)
        }
    }

    private fun onStageLoaded(stageNumber: Int, ad: NativeAd) {
        currentStage = stageNumber
        currentAd = ad
        bindNativeAd(ad, binding)
        if (stageNumber < totalStages) {
            // NativeAds.poll() ở trên đã kích hoạt reload cho stage kế tiếp - nó sẽ sẵn sàng khi user bấm đi tiếp.
            startProgressTimer()
        } else {
            viewModel.onCountdownStarted((durationMs / 1_000L).toInt())
            startCountdownTimer()
        }
    }

    private fun startProgressTimer() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationMs, TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = durationMs - millisUntilFinished
                viewModel.onProgressTick((elapsed * 100 / durationMs).toInt())
            }

            override fun onFinish() {
                viewModel.onProgressFinished()
            }
        }.start()
    }

    private fun startCountdownTimer() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationMs, TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ceil(millisUntilFinished / 1_000.0).toInt()
                    .coerceIn(1, (durationMs / 1_000L).toInt())
                viewModel.onCountdownTick(secondsLeft)
            }

            override fun onFinish() {
                viewModel.onCountdownFinished()
            }
        }.start()
    }

    private fun releaseCurrentAd() {
        countdownTimer?.cancel(); countdownTimer = null
        currentAd?.destroy(); currentAd = null
    }

    private fun finishBreak() {
        if (isAdded) parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
        dismissAllowingStateLoss()
    }

    override fun releaseResources() {
        releaseCurrentAd()
        if (gateHeld) {
            FullScreenGate.release(this); gateHeld = false
        }
    }
}

private fun bindNativeAd(ad: NativeAd, binding: DialogNativeFullBinding) {
    val view = binding.adView
    view.headlineView = binding.tvHeadline
    view.bodyView = binding.tvBody
    view.iconView = binding.ivIcon
    view.callToActionView = binding.btnCta
    view.adChoicesView = binding.adChoicesView

    binding.tvHeadline.text = ad.headline
    binding.tvBody.text = ad.body
    binding.tvBody.visibility = if (ad.body == null) View.GONE else View.VISIBLE
    binding.btnCta.text = ad.callToAction
    binding.ivIcon.setImageDrawable(ad.icon?.drawable)
    binding.ivIcon.visibility = if (ad.icon == null) View.GONE else View.VISIBLE

    view.registerNativeAd(ad, binding.mediaView)
}
