package com.namstd.androidskillhub.core.navigation

enum class StartDestination { ONBOARDING, HOME }

object AppRouter {
    /** Màn đến sau khi chọn ngôn ngữ: vào thẳng [StartDestination.HOME] nếu đã xong setup, nếu chưa thì vào onboarding. */
    fun afterLanguage(setupCompleted: Boolean): StartDestination =
        if (setupCompleted) StartDestination.HOME else StartDestination.ONBOARDING
}
