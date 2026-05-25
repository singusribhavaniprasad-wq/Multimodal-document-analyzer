package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.DocRepository
import com.example.ui.screens.DocIntelDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DocIntelViewModel
import com.example.ui.viewmodel.DocIntelViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repository = DocRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel: DocIntelViewModel = viewModel(
                    factory = DocIntelViewModelFactory(repository)
                )
                DocIntelDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
