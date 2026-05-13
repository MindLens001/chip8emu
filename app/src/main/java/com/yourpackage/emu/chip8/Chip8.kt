package com.yourpackage.emu.chip8

import kotlin.random.Random

val chip8Font = byteArrayOf(
    0xF0.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0xF0.toByte(), // 0
    0x20.toByte(), 0x60.toByte(), 0x20.toByte(), 0x20.toByte(), 0x70.toByte(), // 1
    0xF0.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), // 2
    0xF0.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x10.toByte(), 0xF0.toByte(), // 3
    0x90.toByte(), 0x90.toByte(), 0xF0.toByte(), 0x10.toByte(), 0x10.toByte(), // 4
    0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), 0x10.toByte(), 0xF0.toByte(), // 5
    0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), 0x90.toByte(), 0xF0.toByte(), // 6
    0xF0.toByte(), 0x10.toByte(), 0x20.toByte(), 0x40.toByte(), 0x40.toByte(), // 7
    0xF0.toByte(), 0x90.toByte(), 0xF0.toByte(), 0x90.toByte(), 0xF0.toByte(), // 8
    0xF0.toByte(), 0x90.toByte(), 0xF0.toByte(), 0x10.toByte(), 0xF0.toByte(), // 9
    0xF0.toByte(), 0x90.toByte(), 0xF0.toByte(), 0x90.toByte(), 0x90.toByte(), // A
    0xE0.toByte(), 0x90.toByte(), 0xE0.toByte(), 0x90.toByte(), 0xE0.toByte(), // B
    0xF0.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0xF0.toByte(), // C
    0xE0.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0xE0.toByte(), // D
    0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), // E
    0xF0.toByte(), 0x80.toByte(), 0xF0.toByte(), 0x80.toByte(), 0x80.toByte()  // F
)

class Chip8 {
    val ram = ByteArray(4096)
    val v = IntArray(16)
    var i = 0
    var pc = 0x200
    val stack = IntArray(16)
    var sp = 0
    var dt = 0
    var st = 0
    val fb = BooleanArray(64 * 32)
    val keys = BooleanArray(16)
    var opcode = 0
    var drawFlag = false
    var waitingForKey = false
    var waitingRegister = 0
    var keyWaitingForRelease: Int = -1
    var isDisplayWaiting = false
    fun reset() {
        ram.fill(0)
        v.fill(0)
        i = 0
        pc = 0x200
        stack.fill(0)
        sp = 0
        dt = 0
        st = 0
        fb.fill(false)
        keys.fill(false)
        opcode = 0
        drawFlag = false
        waitingForKey = false
    }

    fun loadRom(data: ByteArray) {
        for (idx in data.indices) {
            if (0x200 + idx < ram.size) {
                ram[0x200 + idx] = data[idx]
            }
        }
    }

    fun loadFont() {
        for (idx in chip8Font.indices) {
            ram[0x50 + idx] = chip8Font[idx]
        }
    }

    fun fetchOpcode() {
        opcode = ((ram[pc].toInt() and 0xff) shl 8) or (ram[pc + 1].toInt() and 0xff)
        pc += 2
    }

    fun tick() {
        if (waitingForKey || isDisplayWaiting) return // Halt for both key and display
        fetchOpcode()
        decodeExecute()
    }

    fun updateTimers() {
        if (dt > 0) dt--
        if (st > 0) st--

        // Every time the timers update (60Hz), we allow drawing again
        isDisplayWaiting = false
    }

    fun keyDown(key: Int) {
        if (key in 0..15) {
            keys[key] = true
            // When a key is pressed during FX0A, we "catch" it but keep waiting
            if (waitingForKey && keyWaitingForRelease == -1) {
                keyWaitingForRelease = key
            }
        }
    }

    fun keyUp(key: Int) {
        if (key in 0..15) {
            keys[key] = false
            // The "Wait for Key" only finishes when the caught key is RELEASED
            if (waitingForKey && key == keyWaitingForRelease) {
                v[waitingRegister] = key
                waitingForKey = false
                keyWaitingForRelease = -1
            }
        }
    }

    private fun decodeExecute() {
        val x = (opcode shr 8) and 0xF
        val y = (opcode shr 4) and 0xF
        val n = opcode and 0xF
        val nn = opcode and 0xFF
        val nnn = opcode and 0xFFF

        when (opcode shr 12) {
            0x0 -> when (nnn) {
                0x0E0 -> {
                    fb.fill(false)
                    drawFlag = true
                }
                0x0EE -> {
                    sp--
                    pc = stack[sp]
                }
            }
            0x1 -> pc = nnn
            0x2 -> {
                stack[sp] = pc
                sp++
                pc = nnn
            }
            0x3 -> if ((v[x] and 0xFF) == nn) pc += 2
            0x4 -> if ((v[x] and 0xFF) != nn) pc += 2
            0x5 -> if ((v[x] and 0xFF) == (v[y] and 0xFF)) pc += 2
            0x6 -> v[x] = nn and 0xFF
            0x7 -> v[x] = ((v[x] and 0xFF) + nn) and 0xFF
            0x8 -> when (n) {
                0x0 -> v[x] = v[y] and 0xFF
                0x1 -> {
                    v[x] = (v[x] or v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x2 -> {
                    v[x] = (v[x] and v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x3 -> {
                    v[x] = (v[x] xor v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x4 -> {
                    val sum = (v[x] and 0xFF) + (v[y] and 0xFF)
                    v[x] = sum and 0xFF
                    v[0xF] = if (sum > 0xFF) 1 else 0
                }
                0x5 -> {
                    val vx = v[x] and 0xFF
                    val vy = v[y] and 0xFF
                    v[x] = (vx - vy) and 0xFF
                    v[0xF] = if (vx >= vy) 1 else 0
                }
                0x6 -> {
                    val vy = v[y] and 0xFF
                    v[x] = (vy shr 1) and 0xFF
                    v[0xF] = vy and 0x1
                }
                0x7 -> {
                    val vx = v[x] and 0xFF
                    val vy = v[y] and 0xFF
                    v[x] = (vy - vx) and 0xFF
                    v[0xF] = if (vy >= vx) 1 else 0
                }
                0xE -> {
                    val vy = v[y] and 0xFF
                    v[x] = (vy shl 1) and 0xFF
                    v[0xF] = (vy and 0x80) shr 7
                }
            }
            0x9 -> if ((v[x] and 0xFF) != (v[y] and 0xFF)) pc += 2
            0xA -> i = nnn
            0xB -> pc = nnn + (v[0] and 0xFF)
            0xC -> v[x] = (Random.nextInt(256) and nn) and 0xFF
            0xD -> {
                val xStart = (v[x] and 0xFF) % 64
                var yPos = (v[y] and 0xFF) % 32
                v[0xF] = 0
                for (row in 0 until n) {
                    if (yPos >= 32) break
                    val spriteByte = ram[i + row].toInt() and 0xFF
                    var xPos = xStart
                    for (q in 0 until 8) {
                        if (xPos >= 64) break
                        if (((spriteByte shr (7 - q)) and 1) == 1) {
                            val idx = yPos * 64 + xPos
                            val pixel = fb[idx]
                            fb[idx] = !fb[idx]
                            if (pixel && !fb[idx]) v[0xF] = 1
                        }
                        xPos++
                    }
                    yPos++
                }
                drawFlag = true
                isDisplayWaiting = true
            }
            0xE -> when (nn) {
                0x9E -> if (keys[(v[x] and 0xFF)]) pc += 2
                0xA1 -> if (!keys[(v[x] and 0xFF)]) pc += 2
            }
            0xF -> when (nn) {
                0x07 -> v[x] = dt and 0xFF
                0x0A -> {
                    // Start the wait state. Logic is handled in keyDown/keyUp
                    waitingForKey = true
                    waitingRegister = x
                    keyWaitingForRelease = -1
                }
                0x15 -> dt = v[x] and 0xFF
                0x18 -> st = v[x] and 0xFF
                0x1E -> i = (i + (v[x] and 0xFF)) and 0xFFFF
                0x29 -> i = 0x50 + ((v[x] and 0x0F) * 5)
                0x33 -> {
                    val vx = v[x] and 0xFF
                    ram[i] = (vx / 100).toByte()
                    ram[i + 1] = ((vx / 10) % 10).toByte()
                    ram[i + 2] = (vx % 10).toByte()
                }
                0x55 -> {
                    for (r in 0..x) ram[i + r] = (v[r] and 0xFF).toByte()
                    i += x + 1
                }
                0x65 -> {
                    for (r in 0..x) v[r] = ram[i + r].toInt() and 0xFF
                    i += x + 1
                }
            }
        }
    }
}
