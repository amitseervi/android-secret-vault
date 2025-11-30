package com.rignis.mysecrets

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.rignis.mysecrets.nav.AppNavigation
import com.rignis.mysecrets.ui.theme.MySecretsTheme
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.activityScope
import org.koin.compose.ComposeContextWrapper
import org.koin.compose.LocalKoinScope
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.scope.Scope

class MainActivity : FragmentActivity(), AndroidScopeComponent {
    override val scope: Scope by activityScope()

    @OptIn(KoinInternalApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {
            CompositionLocalProvider(LocalKoinScope provides ComposeContextWrapper(scope)) {
                MySecretsTheme {
                    AppNavigation()
                }
            }

        }
    }
}