package com.closetheloop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetheloop.ui.MainViewModel
import com.closetheloop.ui.PhoneScreen
import com.closetheloop.ui.PhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                PhoneScreen(viewModel(factory = MainViewModel.Factory), (application as CloseTheLoopPhoneApp).sync)
            }
        }
    }
}
