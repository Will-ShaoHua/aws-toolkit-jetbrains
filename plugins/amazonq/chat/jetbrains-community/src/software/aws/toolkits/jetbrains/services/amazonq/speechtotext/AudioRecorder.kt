// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.speechtotext

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs


object AudioRecorder {
    @Throws(Exception::class)
    fun captureAudio(): ByteArray {
        val format = AudioFormat(16000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val microphone = AudioSystem.getLine(info) as TargetDataLine
        microphone.open(format)
        microphone.start()

        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val silenceThreshold = 500 // Define an amplitude threshold for silence
        println("Recording... Speak into the microphone!")
        val start = System.currentTimeMillis()
        var currentTime: Long? = null
        while (true) {
            val bytesRead = microphone.read(buffer, 0, buffer.size)
            // Convert PCM data to amplitude values
            val amplitudes = buffer.asSequence().chunked(2)
                .map { (low, high) ->
                    (low.toInt() and 0xFF) or (high.toInt() shl 8) // Combine bytes for 16-bit sample
                }.map { abs(it) } // Take absolute value to get amplitude

            // Check if all amplitudes are below the silence threshold
            val isSilent = amplitudes.all { it < silenceThreshold }
            if (!isSilent) {
                println("talking")
                out.write(buffer, 0, bytesRead)
                currentTime = System.currentTimeMillis()
            } else {
                println("silent")
                // no talking for 2 secs, meaning finishing the prompt
                if (currentTime == null) {
                    continue
                }
                if (System.currentTimeMillis() - currentTime < 100) {
                    currentTime = null
                    continue
                }
                if (System.currentTimeMillis() - currentTime > 1500) {
                    break
                }
            }
            if (System.currentTimeMillis() - start > 5000) {
                println("breaking because total time exceeds 5 seconds")
                break
            }
        }
        microphone.close()
        return out.toByteArray()
    }
}
