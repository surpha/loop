package com.closetheloop

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import com.closetheloop.ui.LoopTheme
import com.closetheloop.ui.MainScreen
import com.closetheloop.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private var startVoice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startVoice = intent.wantsVoice()
        setContent {
            LoopTheme {
                AppScaffold {
                    MainScreen(
                        viewModel = viewModel(factory = MainViewModel.Factory),
                        startVoice = startVoice,
                        onStartVoiceHandled = { startVoice = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsVoice()) startVoice = true
    }

    private fun Intent.wantsVoice() = getBooleanExtra(EXTRA_START_VOICE, false)

    companion object {
        /** Tile/Complication deep link: open straight into voice capture. */
        const val EXTRA_START_VOICE = "com.closetheloop.START_VOICE"
    }
}
