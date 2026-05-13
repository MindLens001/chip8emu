package com.yourpackage.emu.chip8

import android.app.Application
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class EmulatorState(
    val pixels: BooleanArray = BooleanArray(64 * 32),
    val running: Boolean = false,
    val romLoaded: Boolean = false,
    val statusMsg: String = "Select a ROM",
    val soundOn: Boolean = false,
    val showRomPicker: Boolean = true      // show picker on launch
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmulatorState) return false
        return running == other.running &&
                romLoaded == other.romLoaded &&
                statusMsg == other.statusMsg &&
                soundOn == other.soundOn &&
                showRomPicker == other.showRomPicker &&
                pixels.contentEquals(other.pixels)
    }
    override fun hashCode() = pixels.contentHashCode()
}

class Chip8ViewModel(app: Application) : AndroidViewModel(app) {

    val chip8 = Chip8()

    private val _state = MutableStateFlow(EmulatorState())
    val state: StateFlow<EmulatorState> = _state

    private var emulatorJob: Job? = null
    private val cyclesPerFrame = 10
    private val frameMs = 16L

    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    init {
        chip8.loadFont()
        initAudio()
    }

    // ── ROM loading ───────────────────────────────────────────

    fun loadRomFromAssets(context: Context, filename: String) {
        try {
            val bytes = context.assets.open(filename).readBytes()
            val name = filename.removeSuffix(".ch8").uppercase()
            loadRom(bytes, name)
        } catch (e: Exception) {
            _state.value = _state.value.copy(statusMsg = "Error: ${e.message}")
        }
    }

    private fun loadRom(bytes: ByteArray, name: String) {
        stopEmulator()
        chip8.reset()
        chip8.loadFont()
        chip8.loadRom(bytes)
        _state.value = _state.value.copy(
            romLoaded = true,
            showRomPicker = false,
            statusMsg = "$name  •  ${bytes.size}B",
            running = false,
            pixels = BooleanArray(64 * 32)
        )
        startEmulator()
    }

    // ── Emulator loop ─────────────────────────────────────────

    fun startEmulator() {
        if (emulatorJob?.isActive == true) return
        _state.value = _state.value.copy(running = true)
        emulatorJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val t0 = System.currentTimeMillis()
                repeat(cyclesPerFrame) {
                    if (!chip8.waitingForKey) chip8.tick()
                }
                chip8.updateTimers()
                val sound = chip8.st > 0
                if (chip8.drawFlag) {
                    chip8.drawFlag = false
                    val snap = chip8.fb.copyOf()
                    _state.value = _state.value.copy(pixels = snap, soundOn = sound)
                } else if (_state.value.soundOn != sound) {
                    _state.value = _state.value.copy(soundOn = sound)
                }
                val elapsed = System.currentTimeMillis() - t0
                val wait = frameMs - elapsed
                if (wait > 0) delay(wait)
            }
        }
    }

    fun stopEmulator() {
        emulatorJob?.cancel()
        emulatorJob = null
        _state.value = _state.value.copy(running = false)
    }

    fun togglePause() {
        if (_state.value.running) {
            stopEmulator()
            _state.value = _state.value.copy(statusMsg = "Paused")
        } else if (_state.value.romLoaded) {
            startEmulator()
            _state.value = _state.value.copy(statusMsg = "Running")
        }
    }

    fun reset() {
        stopEmulator()
        chip8.reset()
        chip8.loadFont()
        _state.value = EmulatorState(showRomPicker = true, statusMsg = "Select a ROM")
    }

    fun showRomPicker() {
        stopEmulator()
        _state.value = _state.value.copy(showRomPicker = true)
    }

    fun keyDown(key: Int) = chip8.keyDown(key)
    fun keyUp(key: Int) = chip8.keyUp(key)

    // ── Audio ─────────────────────────────────────────────────

    private fun initAudio() {
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            bufSize, AudioTrack.MODE_STREAM
        )
        val freq = 440.0
        val buf = ShortArray(bufSize) { idx ->
            (sin(2 * Math.PI * freq * idx / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
        audioTrack?.play()
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (chip8.st > 0) audioTrack?.write(buf, 0, buf.size)
                else delay(10)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulator()
        audioTrack?.stop()
        audioTrack?.release()
    }
}