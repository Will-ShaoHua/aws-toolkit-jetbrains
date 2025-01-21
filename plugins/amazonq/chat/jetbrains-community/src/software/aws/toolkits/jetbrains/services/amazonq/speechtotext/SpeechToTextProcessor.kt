// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.speechtotext

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.cwc.commands.ContextMenuActionMessage
import software.aws.toolkits.jetbrains.services.cwc.commands.EditorContextCommand
import software.aws.toolkits.jetbrains.services.cwc.controller.TestCommandMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage

class TextInputDialog : DialogWrapper(true) {
    private var inputText: String = ""
    init {
        init()
        title = "Enter Text"
    }

    override fun createCenterPanel() = panel {
        row {
            textField()
                .bindText(::inputText)
        }
    }

    fun getInputText(): String {
        return inputText
    }
}


class SpeechToTextProcessor(val project: Project) {
    fun recordAndProcess() {
        try {
            val audio = AudioRecorder.captureAudio()
            processAudio(audio)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    fun processAudio(audioData: ByteArray) {
//        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "/Users/xshaohua/real-codewhisperer-c32330b73659.json");
        SpeechClient.create().use { speechClient ->
            val audioBytes: ByteString = ByteString.copyFrom(audioData)
            val config: RecognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("en-US")
                .build()

            val audio: RecognitionAudio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build()

            val response: RecognizeResponse = speechClient.recognize(config, audio)
            val results = response.getResultsList()
            for (result in results) {
                val prompt = result.getAlternatives(0).getTranscript()
                println("Transcription: " + prompt)

                processPrompt(prompt)
            }
        }
    }

    fun processPrompt(prompt: String) {
        println("Processing prompt: $prompt")
        val trimmedPrompt = prompt.trim()
        val speechType = categorizePrompt(trimmedPrompt)
        println("instruction type: $speechType")
        val window = AmazonQToolWindow.getInstance(project)
        AmazonQToolWindow.showChatWindow(project)
        when(speechType) {
            is SpeechType.HelloQ -> {}

            is SpeechType.Utg -> {
                window.sendMessageAppToUi(TestCommandMessage(), "codetest")
            }

            is SpeechType.GenericChat -> {
                window.sendMessageAppToUi(
                    ChatMessage(
                    tabId = "tab-1",
                    triggerId = "",
                    messageId = "",
                    messageType = ChatMessageType.Prompt,
                    message = prompt,
                    userIntent = null
                ), tabType = "cwc")
                window.sendMessageUiToApp(IncomingCwcMessage.ChatPrompt(prompt, "chat-prompt", "tab-1", null), tabType = "cwc")
            }

            is SpeechType.Refactor -> {
//                window.chatController?.processContextMenuCommand()
                window.sendMessageUiToApp(ContextMenuActionMessage(EditorContextCommand.Refactor, project), tabType = "cwc")
//                window.sendMessageAppToUi(ContextMenuActionMessage(EditorContextCommand.Refactor, project), tabType = "cwc")
            }
        }
    }

    private fun categorizePrompt(prompt: String) : SpeechType {
        val trimmed = prompt.trim()
        return if (PromptPredicate.isHelloPrompt(trimmed)) {
            SpeechType.HelloQ
        } else if (PromptPredicate.isUtgPrompt(trimmed)) {
            SpeechType.Utg
        } else if (PromptPredicate.isRefactor(trimmed)) {
            SpeechType.Refactor
        } else {
            SpeechType.GenericChat
        }
    }
}

object PromptPredicate {
    fun isHelloPrompt(prompt: String): Boolean {
        val helloPattern = """^(hi|hello)\s(amazon|aws|q|\s*)\s*"""
        val trimmed = prompt.trim().lowercase()
        return trimmed.matches(helloPattern.toRegex())
    }

    fun isUtgPrompt(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.contains("test", ignoreCase = true)) {
            return true
        }
        return false
    }

    fun isRefactor(prompt: String): Boolean {
        val trimmed = prompt.trim()
        return trimmed.contains("refactor", ignoreCase = true)
    }
}

sealed interface SpeechType {
    data object HelloQ : SpeechType

    data object GenericChat : SpeechType

    data object Utg : SpeechType

    data object Refactor : SpeechType
}
