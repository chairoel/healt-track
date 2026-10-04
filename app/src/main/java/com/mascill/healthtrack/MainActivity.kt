package com.mascill.healthtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mascill.healthtrack.presentation.navigation.AppNavHost
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthTrackTheme {
                AppNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
