package com.framescope.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.framescope.app.repository.SettingsRepository
import com.framescope.app.ui.featurediscovery.FeatureDiscoveryHost
import com.framescope.app.ui.navigation.FrameScopeNavGraph
import com.framescope.app.ui.theme.FrameScopeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorIndex by settingsRepository.overlayColorIndex.collectAsState()

            FrameScopeTheme(colorIndex = colorIndex) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FeatureDiscoveryHost {
                        FrameScopeNavGraph()
                    }
                }
            }
        }
    }
}
