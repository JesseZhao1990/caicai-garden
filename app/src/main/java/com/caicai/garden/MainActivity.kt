package com.caicai.garden

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.caicai.garden.ui.GardenViewModel
import com.caicai.garden.ui.VisualGardenApp
import com.caicai.garden.ui.theme.CaiCaiTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GardenViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CaiCaiTheme {
                VisualGardenApp(viewModel)
            }
        }
    }
}
