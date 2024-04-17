// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManagerConnection
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeCatalystConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.experiments.ExperimentsActionGroup
import software.aws.toolkits.jetbrains.core.explorer.webview.ToolkitWebviewPanel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.FeatureId
import java.util.concurrent.atomic.AtomicBoolean

class AwsToolkitExplorerFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.helpId = HelpIds.EXPLORER_WINDOW.id

        if (toolWindow is ToolWindowEx) {
            val actionManager = ActionManager.getInstance()
            toolWindow.setTitleActions(listOf(actionManager.getAction("aws.toolkit.explorer.titleBar")))
            toolWindow.setAdditionalGearActions(
                DefaultActionGroup().apply {
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_documentation"),
                            url = AwsToolkit.AWS_DOCS_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_source"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = AwsToolkit.GITHUB_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.create_new_issue"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = "${AwsToolkit.GITHUB_URL}/issues/new/choose"
                        )
                    )
                    add(actionManager.getAction("aws.toolkit.showFeedback"))
                    add(ExperimentsActionGroup())
                    add(actionManager.getAction("aws.settings.show"))
                }
            )
        }

        val contentManager = toolWindow.contentManager

        val content = contentManager.factory.createContent(ToolkitWebviewPanel.getInstance(project).component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        contentManager.addContent(content)
        toolWindow.activate(null)
        contentManager.setSelectedContent(content)

        project.messageBus.connect().subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    connectionChanged(project, newConnection, toolWindow)
                }
            }
        )

        project.messageBus.connect().subscribe(
            AwsConnectionManager.CONNECTION_SETTINGS_STATE_CHANGED,
            object : ConnectionSettingsStateChangeNotifier {
                override fun settingsStateChanged(newState: ConnectionState) {
                    connectionChanged(project, ToolkitConnectionManager.getInstance(project).activeConnection(), toolWindow)
                }
            }
        )
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("aws.notification.title")
    }

    private fun connectionChanged(project: Project, newConnection: ToolkitConnection?, toolWindow: ToolWindow) {
        val isToolkitConnected = when (newConnection) {
            is AwsConnectionManagerConnection -> {
                getLogger<AwsToolkitExplorerFactory>().debug { "IAM connection" }
                true
            }

            is AwsBearerTokenConnection -> {
                val cond1 = CODECATALYST_SCOPES.all { it in newConnection.scopes }
                val cond2 = newConnection.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)

                getLogger<AwsToolkitExplorerFactory>().debug { "Bearer connection: isCodecatalyst=$cond1; isIAM=$cond2" }

                CODECATALYST_SCOPES.all { it in newConnection.scopes } ||
                    newConnection.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
            }

            null -> {
                ToolkitConnectionManager.getInstance(project).let {
                    val conn = it.activeConnection()
                    val hasIamConn = if (conn is AwsBearerTokenConnection) {
                        conn.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
                    } else false

                    val cond2 = it.activeConnectionForFeature(CodeCatalystConnection.getInstance()) != null
                    val cond3 = CredentialManager.getInstance().getCredentialIdentifiers().isNotEmpty()

                    getLogger<AwsToolkitExplorerFactory>().debug { "sign out, checking existing connection(s)... hasIdCPermission=${hasIamConn}; codecatalyst=${cond2}; IAM=${cond3}" }

                    it.activeConnectionForFeature(CodeCatalystConnection.getInstance()) != null ||
                        hasIamConn ||
                        CredentialManager.getInstance().getCredentialIdentifiers().isNotEmpty()
                }
            }

            else -> {
                false
            }
        }

        val contentManager = toolWindow.contentManager
        val component = if (isToolkitConnected) {
            getLogger<AwsToolkitExplorerFactory>().debug { "Rendering explorer tree" }
            AwsToolkitExplorerToolWindow.getInstance(project)
        } else {
            getLogger<AwsToolkitExplorerFactory>().debug { "Rendering signin webview" }
            ToolkitWebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(BrowserState(FeatureId.AwsExplorer))
                it.component
            }
        }
        val myContent = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }

        runInEdt {
            contentManager.removeAllContents(true)
            contentManager.addContent(myContent)
        }
    }

    companion object {
        const val TOOLWINDOW_ID = "aws.toolkit.explorer"
    }
}

fun showWebview(project: Project) {
    val contentManager = AwsToolkitExplorerToolWindow.toolWindow(project).contentManager

    val myContent = contentManager.factory.createContent(ToolkitWebviewPanel.getInstance(project).component, null, false).also {
        it.isCloseable = true
        it.isPinnable = true
    }

    runInEdt {
        contentManager.removeAllContents(true)
        contentManager.addContent(myContent)
    }
}

fun showExplorerTree(project: Project) {
    val contentManager = AwsToolkitExplorerToolWindow.toolWindow(project).contentManager

    val myContent = contentManager.factory.createContent(AwsToolkitExplorerToolWindow.getInstance(project), null, false).also {
        it.isCloseable = true
        it.isPinnable = true
    }

    runInEdt {
        contentManager.removeAllContents(true)
        contentManager.addContent(myContent)
    }
}
