package com.yourpackage.emu.chip8

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val viewModel: Chip8ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Automatically list every .ch8 file inside assets/
        val roms = assets.list("")
            ?.filter { it.endsWith(".ch8") }
            ?.sorted()
            ?: emptyList()

        setContent {
            val state by viewModel.state.collectAsState()

            MainScreen(
                state = state,
                roms = roms,
                onSelectRom = { filename ->
                    viewModel.loadRomFromAssets(this, filename)
                },
                onShowPicker = { viewModel.showRomPicker() },
                onPause = { viewModel.togglePause() },
                onReset = { viewModel.reset() },
                onKeyDown = { viewModel.keyDown(it) },
                onKeyUp = { viewModel.keyUp(it) }
            )
        }
    }
}