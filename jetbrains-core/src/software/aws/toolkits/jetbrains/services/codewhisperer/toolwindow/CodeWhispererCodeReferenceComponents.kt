// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import software.amazon.awssdk.services.codewhisperer.model.Reference
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addHorizontalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addVerticalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.horizontalPanelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.inlineLabelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererLicenseInfoManager
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererConfigurable
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.TOOLWINDOW_BACKGROUND
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.TOOLWINDOW_CODE
import software.aws.toolkits.resources.message
import java.awt.Font
import java.awt.GridBagLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CodeWhispererCodeReferenceComponents(private val project: Project) {
    private val settingsLabelPrefixText = JLabel().apply {
        text = message("codewhisperer.toolwindow.settings.prefix")
    }.asCodeReferencePanelFont()

    private val settingsLabelLink = ActionLink().apply {
        text = message("codewhisperer.toolwindow.settings")
        addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CodeWhispererConfigurable::class.java)
        }
    }.asCodeReferencePanelFont()

    private val settingsPanel = JPanel(GridBagLayout()).apply {
        background = TOOLWINDOW_BACKGROUND
        border = BorderFactory.createEmptyBorder(0, 0, 17, 0)
        add(settingsLabelPrefixText, inlineLabelConstraints)
        add(settingsLabelLink, inlineLabelConstraints)
        add(JLabel("."), inlineLabelConstraints)
        addHorizontalGlue()
    }
    val contentPanel = JPanel(GridBagLayout()).apply {
        background = TOOLWINDOW_BACKGROUND
        border = BorderFactory.createEmptyBorder(7, 14, 0, 0)
        add(settingsPanel, horizontalPanelConstraints)
        addVerticalGlue()
    }

    private val codeReferenceTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val acceptRecommendationPrefixText
        get() = JLabel().apply {
            text = message("codewhisperer.toolwindow.entry.prefix", LocalTime.now().format(codeReferenceTimeFormatter))
        }.asCodeReferencePanelFont()

    private fun licenseNameLink(licenseName: String) = ActionLink(licenseName) {
        BrowserUtil.browse(CodeWhispererLicenseInfoManager.getInstance().getLicenseLink(licenseName))
    }.asCodeReferencePanelFont()

    private fun acceptRecommendationSuffixText(repoName: String, path: String?, line: String, url: String) = JLabel().apply {
        val choice = if (path != null) 1 else 0
        val repo = if (url.isEmpty()) repoName else message("codewhisperer.toolwindow.entry.urlLink", repoName, url)
        text = message("codewhisperer.toolwindow.entry.suffix", repo, path ?: "", choice, line)
    }.asCodeReferencePanelFont()

    fun codeReferenceRecordPanel(ref: Reference, relativePath: String?, lineNums: String) = JPanel(GridBagLayout()).apply {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
        add(acceptRecommendationPrefixText, inlineLabelConstraints)
        add(licenseNameLink(ref.licenseName()), inlineLabelConstraints)
        add(JLabel(" ").asCodeReferencePanelFont(), inlineLabelConstraints)
        add(acceptRecommendationSuffixText(ref.repository(), relativePath, lineNums, ref.url()), inlineLabelConstraints)
        addHorizontalGlue()
    }

    fun codeContentLine(line: String) = JLabel(line).apply {
        foreground = TOOLWINDOW_CODE
    }.asCodeReferencePanelFont()

    fun codeContentPanel(line: String) = JPanel(GridBagLayout()).apply {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        if (line == "") {
            add(codeContentLine(" "), inlineLabelConstraints)
        } else {
            add(codeContentLine(line), inlineLabelConstraints)
        }
        addHorizontalGlue()
    }

    private fun JComponent.asCodeReferencePanelFont(): JComponent {
        font = Font("JetBrains mono", font.style, font.size)
        return this
    }
}
