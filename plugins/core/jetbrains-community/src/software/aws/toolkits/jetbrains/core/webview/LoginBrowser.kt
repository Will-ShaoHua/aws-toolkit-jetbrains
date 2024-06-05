// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.event.Level
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.AuthProfile
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.LastLoginIdcInfo
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sso.PendingAuthorization
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.ssoErrorMessageFromException
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.pollFor
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result
import java.util.concurrent.Future
import java.util.function.Function

data class BrowserState(
    val feature: FeatureId,
    val browserCancellable: Boolean = false,
    val requireReauth: Boolean = false,
    var stage: String = "START",
    val lastLoginIdcInfo: LastLoginIdcInfo = ToolkitAuthManager.getInstance().getLastLoginIdcInfo(),
    val existingConnections: List<AwsBearerTokenConnection> = emptyList()
)

abstract class LoginBrowser(
    private val project: Project,
    val domain: String,
    val webScriptUri: String
) {
    abstract val jcefBrowser: JBCefBrowserBase

    protected val handler = Function<String, JBCefJSQuery.Response> {
        val obj = LOG.tryOrNull("Unable deserialize login browser message: $it", Level.WARN) {
            objectMapper.readValue<BrowserMessage>(it)
        }

        handleBrowserMessage(obj)

        null
    }

    protected var currentAuthorization: PendingAuthorization? = null

    protected data class BearerConnectionSelectionSettings(val currentSelection: AwsBearerTokenConnection, val onChange: (AwsBearerTokenConnection) -> Unit)

    protected val selectionSettings = mutableMapOf<String, BearerConnectionSelectionSettings>()

    protected val onPendingToken: (InteractiveBearerTokenProvider) -> Unit = { provider ->
        projectCoroutineScope(project).launch {
            val authorization = pollForAuthorization(provider)
            if (authorization != null) {
                executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                currentAuthorization = authorization
            }
        }
    }

    @VisibleForTesting
    internal val objectMapper = jacksonObjectMapper()

    abstract fun customize(state: BrowserState): BrowserState

    abstract fun handleBrowserMessage(message: BrowserMessage?)

    fun prepareBrowser(state: BrowserState) {
        selectionSettings.clear()

        customize(state)

        val jsonData = """
            {
                stage: '${state.stage}',
                regions: ${objectMapper.writeValueAsString(AwsRegionProvider.getInstance().allRegionsForService("sso").values)},
                idcInfo: {
                    profileName: '${state.lastLoginIdcInfo.profileName}',
                    startUrl: '${state.lastLoginIdcInfo.startUrl}',
                    region: '${state.lastLoginIdcInfo.region}'
                },
                cancellable: ${state.browserCancellable},
                feature: '${state.feature}',
                existConnections: ${objectMapper.writeValueAsString(selectionSettings.values.map { it.currentSelection }.toList())}
            }
        """.trimIndent()
        executeJS("window.ideClient.prepareUi($jsonData)")
    }

    protected fun executeJS(jsScript: String) {
        this.jcefBrowser.cefBrowser.let {
            it.executeJavaScript(jsScript, it.url, 0)
        }
    }

    protected fun cancelLogin() {
        // Essentially Authorization becomes a mutable that allows browser and auth to communicate canceled
        // status. There might be a risk of race condition here by changing this global, for which effort
        // has been made to avoid it (e.g. Cancel button is only enabled if Authorization has been given
        // to browser.). The worst case is that the user will see a stale user code displayed, but not
        // affecting the current login flow.
        currentAuthorization?.progressIndicator?.cancel()
        // TODO: telemetry
    }

    fun userCodeFromAuthorization(authorization: PendingAuthorization) = when (authorization) {
        is PendingAuthorization.DAGAuthorization -> authorization.authorization.userCode
        else -> ""
    }

    fun loginBuilderId(scopes: List<String>) {
        loginWithBackgroundContext {
            val h = object : BearerLoginHandler {
                override fun onSuccess() {
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        credentialStartUrl = SONO_URL,
                        result = Result.Succeeded,
                        credentialSourceId = CredentialSourceId.AwsId
                    )
                }

                override fun onPendingToken(provider: InteractiveBearerTokenProvider) {
                    projectCoroutineScope(project).launch {
                        val authorization = pollForAuthorization(provider)
                        if (authorization != null) {
                            executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                            currentAuthorization = authorization
                        }
                    }
                }

                override fun onError(e: Exception) {
                    tryHandleUserCanceledLogin(e)
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        credentialStartUrl = SONO_URL,
                        result = Result.Failed,
                        reason = e.message,
                        credentialSourceId = CredentialSourceId.AwsId
                    )
                }
            }

            Login.BuilderId(scopes, h).login(project)
        }
    }

    open fun loginIdC(url: String, region: AwsRegion, scopes: List<String>) {
        val h = object : BearerLoginHandler {
            override fun onPendingToken(provider: InteractiveBearerTokenProvider) {
                projectCoroutineScope(project).launch {
                    val authorization = pollForAuthorization(provider)
                    if (authorization != null) {
                        executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                        currentAuthorization = authorization
                    }
                }
            }

            override fun onSuccess() {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = url,
                    result = Result.Succeeded,
                    credentialSourceId = CredentialSourceId.IamIdentityCenter
                )
            }

            override fun onError(e: Exception) {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = url,
                    result = Result.Failed,
                    reason = e.message,
                    credentialSourceId = CredentialSourceId.IamIdentityCenter
                )
            }

            override fun onError(e: Exception, authProfile: AuthProfile) {
                val message = ssoErrorMessageFromException(e)
                if (!tryHandleUserCanceledLogin(e)) {
                    LOG.error(e) { "Failed to authenticate: message: $message; profile: $authProfile" }
                }
            }
        }

        loginWithBackgroundContext {
            Login.IdC(url, region, scopes, h).login(project)
        }
    }

    open fun loginIAM(profileName: String, accessKey: String, secretKey: String) {
        runInEdt {
            Login.LongLivedIAM(
                profileName,
                accessKey,
                secretKey
            ).login(project)
            AwsTelemetry.loginWithBrowser(
                project = null,
                result = Result.Succeeded,
                credentialType = CredentialType.StaticProfile
            )
        }
    }

    protected fun <T> loginWithBackgroundContext(action: () -> T): Future<T> =
        ApplicationManager.getApplication().executeOnPooledThread<T> {
            runBlocking {
                withBackgroundProgress(project, message("credentials.pending.title")) {
                    blockingContext {
                        action()
                    }
                }
            }
        }

    abstract fun loadWebView(query: JBCefJSQuery)

    protected suspend fun pollForAuthorization(provider: InteractiveBearerTokenProvider): PendingAuthorization? = pollFor {
        provider.pendingAuthorization
    }

    protected fun reauth(connection: ToolkitConnection?) {
        if (connection is AwsBearerTokenConnection) {
            loginWithBackgroundContext {
                reauthConnectionIfNeeded(project, connection, onPendingToken)
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    isReAuth = true,
                    result = Result.Succeeded,
                    credentialStartUrl = connection.startUrl,
                    credentialType = CredentialType.BearerToken
                )
            }
        }
    }

    protected fun tryHandleUserCanceledLogin(e: Exception): Boolean {
        if (e !is ProcessCanceledException ||
            e.cause !is IllegalStateException ||
            e.message?.contains(message("credentials.pending.user_cancel.message")) == false
        ) {
            return false
        }
        LOG.debug(e) { "User canceled login" }
        return true
    }

    companion object {
        private val LOG = getLogger<LoginBrowser>()
        fun getWebviewHTML(webScriptUri: String, query: JBCefJSQuery): String {
            val colorMode = if (JBColor.isBright()) "jb-light" else "jb-dark"
            val postMessageToJavaJsCode = query.inject("JSON.stringify(message)")

            val jsScripts = """
            <script>
                (function() {
                    window.ideApi = {
                     postMessage: message => {
                         $postMessageToJavaJsCode
                     }
                };
                }())
            </script>
            <script type="text/javascript" src="$webScriptUri"></script>
            """.trimIndent()

            return """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>AWS Q</title>
                </head>
                <body class="$colorMode">
                    <div id="app"></div>
                    $jsScripts
                </body>
            </html>
            """.trimIndent()
        }
    }
}
