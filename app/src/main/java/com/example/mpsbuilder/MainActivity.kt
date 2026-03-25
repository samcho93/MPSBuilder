package com.example.mpsbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.mpsbuilder.ui.theme.MPSBuilderTheme
import com.example.mpsbuilder.ui.workbench.WorkbenchBuilderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MPSBuilderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkbenchBuilderScreen()
                }
            }
        }
    }
}
