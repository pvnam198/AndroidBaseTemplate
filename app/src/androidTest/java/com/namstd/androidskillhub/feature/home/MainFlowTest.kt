package com.namstd.androidskillhub.feature.home

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.preferences.AppPreferences
import com.namstd.androidskillhub.feature.language.LanguageActivity
import com.namstd.androidskillhub.feature.main.MainActivity
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end first-run flow and the Settings/Premium path. Ads are switched off so the flow never waits on
 * the network, and the Language delay is overridden to 0 so its confirm button is there at once.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        preferences().edit().clear().commit()
        debugOverrides().edit().clear().putString("language_delay_millis", "0").commit()
        Ads.adsEnabled = false
        InterstitialAds.clear()
    }

    @After fun tearDown() {
        preferences().edit().clear().commit()
        debugOverrides().edit().clear().commit()
        Ads.adsEnabled = true
        InterstitialAds.clear()
    }

    @Test fun firstRunThenRelaunchCompletesFullFlowWithoutNetwork() {
        ActivityScenario.launch(LanguageActivity::class.java).use {
            onView(withText(R.string.choose_language)).check(matches(isDisplayed()))
            onView(withText("English")).perform(click())
            waitUntilDisplayed(withId(R.id.apply_button))
            onView(withId(R.id.apply_button)).perform(click())

            waitUntilDisplayed(withText(R.string.onboarding_title_1))
            onView(withId(R.id.next_button)).perform(click())
            waitUntilDisplayed(withText(R.string.onboarding_title_2))
            onView(withId(R.id.next_button)).perform(click())
            waitUntilDisplayed(withText(R.string.onboarding_title_3))
            onView(withId(R.id.next_button)).perform(click())
            // No full-screen native page by default (native_intro_full = 0): four content pages in a row.
            waitUntilDisplayed(withText(R.string.onboarding_title_4))
            onView(withId(R.id.next_button)).perform(click())

            waitUntilDisplayed(withId(R.id.main_feed_tabs))
        }
        assertTrue(AppPreferences.getInstance(context).setupCompleted)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntilDisplayed(withId(R.id.main_feed_tabs))
        }
    }

    @Test fun settingsResetAndPremiumUpgrade() {
        preferences().edit().putBoolean("setup_completed", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.ivSetting)).perform(click())
            waitUntilDisplayed(withId(R.id.reset_onboarding_button))
            onView(withId(R.id.reset_onboarding_button)).perform(click())
            onView(withId(R.id.premium_button)).perform(click())
            waitUntilDisplayed(withId(R.id.upgrade_button))
            onView(withId(R.id.upgrade_button)).perform(click())
            onView(withText(R.string.premium_active)).check(matches(isDisplayed()))
        }
        val prefs = AppPreferences.getInstance(context)
        assertTrue(prefs.isPremium)
        assertTrue(!prefs.setupCompleted)
    }

    /** Polls until [matcher] is on screen - covers screen transitions and ViewPager2 page scrolls. */
    private fun waitUntilDisplayed(matcher: Matcher<View>, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (error: Throwable) {
                if (System.currentTimeMillis() > deadline) throw error
                Thread.sleep(100)
            }
        }
    }

    private fun preferences() = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    private fun debugOverrides() = context.getSharedPreferences("debug_overrides", Context.MODE_PRIVATE)
}
