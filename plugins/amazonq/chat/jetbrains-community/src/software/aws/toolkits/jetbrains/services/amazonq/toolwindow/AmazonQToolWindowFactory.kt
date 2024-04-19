// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.aggregateQConnectionState
import software.aws.toolkits.jetbrains.core.credentials.sono.CODEWHISPERER_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.services.amazonq.WebviewPanel
import software.aws.toolkits.jetbrains.services.amazonq.isQSupportedInThisVersion
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.FeatureId

class AmazonQToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager

        project.messageBus.connect().subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    onConnectionChanged(project, newConnection, toolWindow)
                }
            }
        )

        val qConnState = aggregateQConnectionState(project)
        val component = if (qConnState == BearerTokenAuthState.AUTHORIZED) {
            AmazonQToolWindow.getInstance(project).component
        } else if (qConnState == BearerTokenAuthState.NEEDS_REFRESH) {
            LOG.debug { "returning login window; no Q connection found" }
            WebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(BrowserState(FeatureId.Q, reauth = true))
                it.component
            }
        } else {
            WebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(BrowserState(FeatureId.Q))
                it.component
            }
        }

        val content = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        contentManager.addContent(content)
        toolWindow.activate(null)
        contentManager.setSelectedContent(content)
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("q.window.title")
    }

    override fun shouldBeAvailable(project: Project): Boolean = !isRunningOnRemoteBackend() && isQSupportedInThisVersion()

    private fun onConnectionChanged(project: Project, newConnection: ToolkitConnection?, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val isNewConnectionQConnection = newConnection?.let {
            (it as? AwsBearerTokenConnection)?.let { conn ->
                val scopeShouldHave = Q_SCOPES + CODEWHISPERER_SCOPES

                LOG.debug { "newConnection: ${conn.id}; scope: ${conn.scopes}; scope must-have: $scopeShouldHave" }

                scopeShouldHave.all { s -> s in conn.scopes }
            } ?: false
        } ?: false

        val qConnState = aggregateQConnectionState(project)

        // isQConnected alone is not robust and there is race condition (read/update connection states)
        val component = if (isNewConnectionQConnection || qConnState == BearerTokenAuthState.AUTHORIZED) {
            AmazonQToolWindow.getInstance(project).component
        } else if (qConnState == BearerTokenAuthState.NEEDS_REFRESH) {
            LOG.debug { "returning login window; no Q connection found" }
            WebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(BrowserState(FeatureId.Q, reauth = true))
                it.component
            }
        } else {
            WebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(BrowserState(FeatureId.Q))
                it.component
            }
        }

        val content = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }

        runInEdt {
            contentManager.removeAllContents(true)
            contentManager.addContent(content)
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQToolWindowFactory>()
        const val WINDOW_ID = AMAZON_Q_WINDOW_ID
    }
}
