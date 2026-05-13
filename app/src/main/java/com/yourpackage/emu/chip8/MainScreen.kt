package com.yourpackage.emu.chip8

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colours ────────────────────────────────────────────────
private val BgDeep       = Color(0xFF060C11)
private val BgPanel      = Color(0xFF0B1520)
private val NeonGreenDim = Color(0xFF00AA55)
private val KeyBg        = Color(0xFF0D1E18)
private val KeyBgAccent  = Color(0xFF0A1A22)
private val KeyBorder    = Color(0xFF1A4A30)
private val KeyBorderAcc = Color(0xFF1A3A4A)
private val HeaderLine   = Color(0xFF1A3A2A)
private val DimText      = Color(0xFF2A5A3A)

// CHIP-8 keypad layout: label → chip8 hex key
private val keypadRows = listOf(
    listOf("1" to 0x1, "2" to 0x2, "3" to 0x3, "C" to 0xC),
    listOf("4" to 0x4, "5" to 0x5, "6" to 0x6, "D" to 0xD),
    listOf("7" to 0x7, "8" to 0x8, "9" to 0x9, "E" to 0xE),
    listOf("A" to 0xA, "0" to 0x0, "B" to 0xB, "F" to 0xF),
)

// ── Root screen ────────────────────────────────────────────

@Composable
fun MainScreen(
    state: EmulatorState,
    roms: List<String>,
    onSelectRom: (String) -> Unit,
    onShowPicker: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .systemBarsPadding()
    ) {
        // Main emulator UI always behind
        Column(modifier = Modifier.fillMaxSize()) {
            Header(statusMsg = state.statusMsg, soundOn = state.soundOn)
            HDivider()

            // Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .aspectRatio(2f)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(NeonGreen, NeonBlue)),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Chip8Display(pixels = state.pixels)
            }

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ControlButton(
                    label = "ROMS",
                    modifier = Modifier.weight(1f),
                    color = NeonGreen,
                    onClick = onShowPicker
                )
                ControlButton(
                    label = if (state.running) "⏸" else "▶",
                    modifier = Modifier.weight(0.55f),
                    color = NeonGreen,
                    onClick = onPause
                )
                ControlButton(
                    label = "RESET",
                    modifier = Modifier.weight(0.65f),
                    color = Color(0xFFFF4455),
                    onClick = onReset
                )
            }

            HDivider()

            Keypad(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                onKeyDown = onKeyDown,
                onKeyUp = onKeyUp
            )
        }

        // ROM Picker overlay
        if (state.showRomPicker) {
            RomPickerOverlay(
                roms = roms,
                onSelect = onSelectRom
            )
        }
    }
}

// ── ROM picker overlay ─────────────────────────────────────

@Composable
private fun RomPickerOverlay(
    roms: List<String>,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC060C11)),   // dim background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(NeonGreen, NeonBlue)),
                    RoundedCornerShape(10.dp)
                )
                .background(BgPanel)
                .padding(20.dp)
        ) {
            Text(
                text = "SELECT ROM",
                color = NeonGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "bundled games",
                color = DimText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (roms.isEmpty()) {
                Text(
                    text = "No .ch8 files found in assets/",
                    color = Color(0xFFFF4455),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(roms) { rom ->
                        RomRow(rom = rom, onSelect = onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun RomRow(rom: String, onSelect: (String) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) NeonGreen else Color.Transparent,
        animationSpec = tween(80), label = "romBg"
    )
    val textColor by animateColorAsState(
        if (pressed) BgDeep else NeonGreen,
        animationSpec = tween(80), label = "romText"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KeyBorder, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .pointerInput(rom) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onSelect(rom)
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "▸",
            color = NeonGreenDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = rom.removeSuffix(".ch8").uppercase(),
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ".ch8",
            color = DimText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

// ── Header ─────────────────────────────────────────────────

@Composable
private fun Header(statusMsg: String, soundOn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CHIP-8",
            color = NeonGreen,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.weight(1f)
        )
        if (soundOn) {
            Text(
                text = "♪  ",
                color = NeonGreen,
                fontSize = 16.sp
            )
        }
        Text(
            text = statusMsg,
            color = NeonGreenDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HeaderLine)
    )
}

// ── Control buttons ────────────────────────────────────────

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = NeonGreen,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) color else Color.Transparent,
        animationSpec = tween(80), label = "ctrlBg"
    )
    val textColor by animateColorAsState(
        if (pressed) BgDeep else color,
        animationSpec = tween(80), label = "ctrlText"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .border(1.dp, color, RoundedCornerShape(5.dp))
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

// ── Keypad ─────────────────────────────────────────────────

@Composable
private fun Keypad(
    modifier: Modifier = Modifier,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keypadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (label, chip8Key) ->
                    KeyButton(
                        label = label,
                        chip8Key = chip8Key,
                        isAccent = label.first().isLetter(),
                        modifier = Modifier.weight(1f),
                        onKeyDown = onKeyDown,
                        onKeyUp = onKeyUp
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    chip8Key: Int,
    isAccent: Boolean,
    modifier: Modifier = Modifier,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = when {
            pressed && isAccent -> NeonBlue
            pressed -> NeonGreen
            isAccent -> KeyBgAccent
            else -> KeyBg
        },
        animationSpec = tween(60), label = "keyBg"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            pressed -> BgDeep
            isAccent -> NeonBlue
            else -> NeonGreen
        },
        animationSpec = tween(60), label = "keyText"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        animationSpec = tween(60), label = "keyScale"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .border(1.dp, if (isAccent) KeyBorderAcc else KeyBorder, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .pointerInput(chip8Key) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onKeyDown(chip8Key)
                        tryAwaitRelease()
                        pressed = false
                        onKeyUp(chip8Key)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}