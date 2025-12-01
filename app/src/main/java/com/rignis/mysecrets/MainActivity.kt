package com.rignis.mysecrets

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rignis.mysecrets.nav.AppNavigation
import com.rignis.mysecrets.ui.theme.AppTheme
import com.rignis.store.api.SettingsRepository
import com.rignis.store.api.UserThemePreference
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.activityScope
import org.koin.compose.ComposeContextWrapper
import org.koin.compose.LocalKoinScope
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.scope.Scope

class MainActivity : FragmentActivity(), AndroidScopeComponent {
    override val scope: Scope by activityScope()

    @OptIn(KoinInternalApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API level 33)
            setRecentsScreenshotEnabled(false)
        }
        installSplashScreen()

        setContent {
            CompositionLocalProvider(LocalKoinScope provides ComposeContextWrapper(scope)) {
                val settingsRepository: SettingsRepository = koinInject()
                val themeState = settingsRepository.userPreferredTheme.collectAsStateWithLifecycle(
                    UserThemePreference.SYSTEM
                )
                AppTheme(themeState.value) {
                    AppNavigation()
                }
            }

        }
    }
}