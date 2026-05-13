package com.yourpackage.emu.chip8

import kotlin.random.Random

val chip8Font = byteArrayOf(
    //in kotlin hex are automatically treated as ints so we explicitly convert them to byte
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
    val ram = ByteArray(4096) //ram of chip8
    val v = IntArray(16) //registers of chip8
    var i = 0 //Index register of chip8
    var pc = 0x200
    val stack = IntArray(16)
    var sp = 0  //stack pointer of chip8
    var dt = 0  //delay timer
    var st = 0  //sound timer
    val fb = BooleanArray(64 * 32)  //frame buffer
    val keys = BooleanArray(16)  //keys for workings
    var opcode = 0  //Initialising opcode
    var drawFlag = false //init draw flag
    var waitingForKey = false
    var waitingRegister = 0
    var keyWaitingForRelease: Int = -1
    var isDisplayWaiting = false
    fun reset() { //resets the chip 8 state so we can load new roms or play new game
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

    fun loadRom(data: ByteArray) { //loads our game file into ram
        for (idx in data.indices) {
            if (0x200 + idx < ram.size) { //0x200 in decimal is 512, the first 512 is for interpreter in old times which we leave empty, so we start loading at 0x200
                ram[0x200 + idx] = data[idx]
            }
        }
    }

    fun loadFont() { //loads our predefined font into ram at 0x50
        for (idx in chip8Font.indices) {
            ram[0x50 + idx] = chip8Font[idx]
        }
    }

    fun fetchOpcode() { //takes the pc and pc + 1 bytes and packs it in opcode. Pc is later incremented by 2 to point to next instruction
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

    //if key is down or pressed
    fun keyDown(key: Int) {
        if (key in 0..15) {
            keys[key] = true
            // When a key is pressed during FX0A, we "catch" it but keep waiting
            if (waitingForKey && keyWaitingForRelease == -1) {
                keyWaitingForRelease = key
            }
        }
    }

    //if key is released
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
        val x = (opcode shr 8) and 0xF //we are extracting the x val
        val y = (opcode shr 4) and 0xF // extract the y
        val n = opcode and 0xF //extract the last nibble(4 bits)
        val nn = opcode and 0xFF //extract the byte(8 bits)
        val nnn = opcode and 0xFFF //extract the address which will be total 12 bits

        when (opcode shr 12) { //we are doing shr 12 to extract the high nibble to find which family of opcode it is.
                               // ex: opcode = 6010, shr 12 extract 6010 >> 12 = 6 then we jump to 0x06
            0x0 -> when (nnn) {
                0x0E0 -> { //instruction cls clears the screen
                    fb.fill(false)
                    drawFlag = true
                }
                0x0EE -> { //return from subroutine(function)
                    sp--   // we decrement the stack pointer and get the ret address
                    pc = stack[sp] //we set the pc to the ret address
                }
            }
            0x1 -> pc = nnn // 1nnn - JP addr. Jump to location nnn.
            0x2 -> { // 2nnn - CALL addr. Call subroutine at nnn. The interpreter increments the stack pointer, then puts the current PC on the top of the stack. The PC is then set to nnn.

                stack[sp] = pc
                sp++
                pc = nnn
            }
            0x3 -> if ((v[x] and 0xFF) == nn) pc += 2 //3xkk - SE Vx, byte. Skip next instruction if Vx = kk.
            0x4 -> if ((v[x] and 0xFF) != nn) pc += 2 //4xkk - SNE Vx, byte. Skip next instruction if Vx != kk.
            0x5 -> if ((v[x] and 0xFF) == (v[y] and 0xFF)) pc += 2 //5xy0 - SE Vx, Vy. Skip next instruction if Vx = Vy.
            0x6 -> v[x] = nn and 0xFF //6xkk - LD Vx, byte. Set Vx = kk.
            0x7 -> v[x] = ((v[x] and 0xFF) + nn) and 0xFF //7xkk - ADD Vx, byte. Set Vx = Vx + kk.
            0x8 -> when (n) {
                0x0 -> v[x] = v[y] and 0xFF //8xy0 - LD Vx, Vy Set Vx = Vy.
                0x1 -> { //8xy1 - OR Vx, Vy. Set Vx = Vx OR Vy.
                    v[x] = (v[x] or v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x2 -> { //8xy2 - AND Vx, Vy. Set Vx = Vx AND Vy.
                    v[x] = (v[x] and v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x3 -> { //8xy3 - XOR Vx, Vy. Set Vx = Vx XOR Vy.
                    v[x] = (v[x] xor v[y]) and 0xFF
                    v[0xF] = 0
                }
                0x4 -> { //8xy4 - ADD Vx, Vy. Set Vx = Vx + Vy, set VF = carry.
                    val sum = (v[x] and 0xFF) + (v[y] and 0xFF)
                    v[x] = sum and 0xFF
                    v[0xF] = if (sum > 0xFF) 1 else 0
                }
                0x5 -> { //8xy5 - SUB Vx, Vy. Set Vx = Vx - Vy, set VF = NOT borrow.
                    val vx = v[x] and 0xFF
                    val vy = v[y] and 0xFF
                    v[x] = (vx - vy) and 0xFF
                    v[0xF] = if (vx >= vy) 1 else 0
                }
                0x6 -> { //8xy6 - SHR Vx {, Vy}. Set Vx = Vx SHR 1. which basically is Vx = Vx / 2
                    val vy = v[y] and 0xFF
                    v[x] = (vy shr 1) and 0xFF
                    v[0xF] = vy and 0x1
                }
                0x7 -> { // 8xy7 - SUBN Vx, Vy. If Vy > Vx, then VF is set to 1, otherwise 0. Then Vx is subtracted from Vy, and the results stored in Vx.
                    val vx = v[x] and 0xFF
                    val vy = v[y] and 0xFF
                    v[x] = (vy - vx) and 0xFF
                    v[0xF] = if (vy >= vx) 1 else 0
                }
                0xE -> { //8xyE - SHL Vx {, Vy}. Set Vx = Vx SHL 1. which basically is Vx = Vx * 2
                    val vy = v[y] and 0xFF
                    v[x] = (vy shl 1) and 0xFF
                    v[0xF] = (vy and 0x80) shr 7
                }
            }
            0x9 -> if ((v[x] and 0xFF) != (v[y] and 0xFF)) pc += 2 //9xy0 - SNE Vx, Vy. Skip next instruction if Vx != Vy.
            0xA -> i = nnn //Annn - LD I, addr. Set I = nnn.
            0xB -> pc = nnn + (v[0] and 0xFF) //Bnnn - JP V0, addr. Jump to location nnn + V0.
            0xC -> v[x] = (Random.nextInt(256) and nn) and 0xFF //Cxkk - RND Vx, byte. Set Vx = random byte AND kk.
            0xD -> { //Dxyn - DRW Vx, Vy, nibble. Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
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
                0x9E -> if (keys[(v[x] and 0xFF)]) pc += 2 //Ex9E - SKP Vx Ex9E - SKP Vx
                0xA1 -> if (!keys[(v[x] and 0xFF)]) pc += 2 //ExA1 - SKNP Vx ExA1 - SKNP Vx
            }
            0xF -> when (nn) {
                0x07 -> v[x] = dt and 0xFF //Fx07 - LD Vx, DT Set Vx = delay timer value.
                0x0A -> { //Fx0A - LD Vx, K Wait for key press, store key in Vx.
                    // Start the wait state. Logic is handled in keyDown/keyUp
                    waitingForKey = true
                    waitingRegister = x
                    keyWaitingForRelease = -1
                }
                0x15 -> dt = v[x] and 0xFF //Fx15 - LD DT, Vx. Set delay timer = Vx.
                0x18 -> st = v[x] and 0xFF //Fx18 - LD ST, Vx. Set sound timer = Vx.
                0x1E -> i = (i + (v[x] and 0xFF)) and 0xFFFF //Fx1E - ADD I, Vx. Set I = I + Vx.
                0x29 -> i = 0x50 + ((v[x] and 0x0F) * 5) //Fx29 - LD F, Vx. Set I = location of sprite for digit Vx.
                0x33 -> { //Fx33 - LD B, Vx. Store BCD representation of Vx in memory locations I, I+1, and I+2.
                    val vx = v[x] and 0xFF
                    ram[i] = (vx / 100).toByte()
                    ram[i + 1] = ((vx / 10) % 10).toByte()
                    ram[i + 2] = (vx % 10).toByte()
                }
                0x55 -> { //Fx55 - LD [I], Vx. Store registers V0 through Vx in memory starting at location I.
                    for (r in 0..x) ram[i + r] = (v[r] and 0xFF).toByte()
                    i += x + 1
                }
                0x65 -> { //Fx65 - LD Vx, [I] Store registers V0 through Vx in memory starting at location I.
                    for (r in 0..x) v[r] = ram[i + r].toInt() and 0xFF
                    i += x + 1
                }
            }
        }
    }
}
