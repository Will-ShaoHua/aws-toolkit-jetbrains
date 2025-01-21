// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.speechtotext

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class StartRecordingAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // TODO: uncomment
//            val audioData: ByteArray = AudioRecorder.captureAudio()
//            SpeechToTextProcessor(project).processAudio(audioData)
        // TODO: remove the following dev purpose code
        val inputBox = TextInputDialog()
        if (inputBox.showAndGet()) {
            SpeechToTextProcessor(project).processPrompt(inputBox.getInputText())
        }
//        SpeechToTextProcessor(project).processPrompt("write a function to sum 2 numbers")
//        SpeechToTextProcessor(project).processPrompt("/test add")
    }
}
