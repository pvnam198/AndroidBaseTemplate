package com.namstd.androidskillhub.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPagesTest {
    private val ad = -1

    @Test
    fun noAdPageByDefault() {
        assertEquals(listOf(0, 1, 2, 3), OnboardingActivity.buildPages(4, emptySet()))
    }

    @Test
    fun adPageGoesAfterEachListedContentPage() {
        assertEquals(listOf(0, 1, ad, 2, 3), OnboardingActivity.buildPages(4, setOf(2)))
        assertEquals(listOf(0, ad, 1, ad, 2, ad, 3), OnboardingActivity.buildPages(4, setOf(1, 2, 3)))
        assertEquals(listOf(0, ad, 1, 2, ad, 3), OnboardingActivity.buildPages(4, setOf(1, 3)))
    }

    @Test
    fun neverAfterTheLastContentPage() {
        assertEquals(listOf(0, 1, 2, 3), OnboardingActivity.buildPages(4, setOf(4)))
        assertEquals(listOf(0, 1, ad, 2), OnboardingActivity.buildPages(3, setOf(2, 3)))
    }
}
