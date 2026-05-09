package com.giannisliu.melodyfitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.giannisliu.melodyfitness.ui.FitnessViewModel
import com.giannisliu.melodyfitness.ui.MelodyFitnessApp
import com.giannisliu.melodyfitness.ui.theme.MelodyFitnessTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FitnessViewModel by viewModels {
        FitnessViewModel.Factory(
            repository = (application as MelodyFitnessApplication).repository,
            settingsRepository = (application as MelodyFitnessApplication).settingsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MelodyFitnessTheme {
                MelodyFitnessApp(viewModel = viewModel)
            }
        }
    }
}
