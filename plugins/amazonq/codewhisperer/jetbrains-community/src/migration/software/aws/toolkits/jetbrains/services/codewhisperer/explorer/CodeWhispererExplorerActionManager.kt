// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.aws.toolkits.jetbrains.services.codewhisperer.explorer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreActionState
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreStateType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl

// TODO: refactor this class, now it's managing action and state
@State(name = "codewhispererStates", storages = [Storage("aws.xml")])
class CodeWhispererExplorerActionManager : PersistentStateComponent<CodeWhispererExploreActionState> {
    private val actionState = CodeWhispererExploreActionState()
    private val suspendedConnections = mutableSetOf<String>()

    fun setSuspended(project: Project) {
        val startUrl = getCodeWhispererConnectionStartUrl(project)
        if (!suspendedConnections.add(startUrl)) {
            return
        }
    }

    private fun getCodeWhispererConnectionStartUrl(project: Project): String {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        return getConnectionStartUrl(connection) ?: run {
            LOG.warn { "fail to retrieve connection start url, not a bearer token connection" }
            ""
        }
    }

    fun isAutoEnabled(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsAutoEnabled, true)

    fun setAutoEnabled(isAutoEnabled: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsAutoEnabled] = isAutoEnabled
    }

    fun isAutoEnabledForCodeScan(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsAutoCodeScanEnabled, true)

    fun setAutoEnabledForCodeScan(isAutoEnabledForCodeScan: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsAutoCodeScanEnabled] = isAutoEnabledForCodeScan
    }

    fun isMonthlyQuotaForCodeScansExceeded(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsMonthlyQuotaForCodeScansExceeded, false)

    fun setMonthlyQuotaForCodeScansExceeded(isMonthlyQuotaForCodeScansExceeded: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsMonthlyQuotaForCodeScansExceeded] = isMonthlyQuotaForCodeScansExceeded
    }

    fun setHasShownNewOnboardingPage(hasShownNewOnboardingPage: Boolean) {
        actionState.value[CodeWhispererExploreStateType.HasShownNewOnboardingPage] = hasShownNewOnboardingPage
    }

    fun getConnectionExpiredDoNotShowAgain(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain, false)

    fun setConnectionExpiredDoNotShowAgain(doNotShowAgain: Boolean) {
        actionState.value[CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain] = doNotShowAgain
    }

    fun getSessionConfigurationMessageShown(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.SessionConfigurationMessageShown, false)

    fun setSessionConfigurationMessageShown(isShown: Boolean) {
        actionState.value[CodeWhispererExploreStateType.SessionConfigurationMessageShown] = isShown
    }

    fun setAutoSuggestion(isAutoEnabled: Boolean) {
        setAutoEnabled(isAutoEnabled)
    }

    // Adding Auto CodeScan Function
    fun setAutoCodeScan(isAutoEnabledForCodeScan: Boolean) {
        setAutoEnabledForCodeScan(isAutoEnabledForCodeScan)
    }

    fun getIsFirstRestartAfterQInstall(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsFirstRestartAfterQInstall, true)

    fun setIsFirstRestartAfterQInstall(isFirstRestartAfterQInstall: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsFirstRestartAfterQInstall] = isFirstRestartAfterQInstall
    }

    @Deprecated("Accountless credential will be removed soon")
    @ApiStatus.ScheduledForRemoval
    // Will keep it for existing accountless users
    /**
     * Will be called from CodeWhispererService.showRecommendationInPopup()
     * Caller (e.x. CodeWhispererService) should take care if null value returned, popup a notification/hint window or dialog etc.
     */
    fun resolveAccessToken(): String? {
        if (actionState.token == null) {
            LOG.warn { "Logical Error: Try to get access token before token initialization" }
        }
        return actionState.token
    }

    fun checkActiveCodeWhispererConnectionType(project: Project): CodeWhispererLoginType {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance()) as? AwsBearerTokenConnection
        return conn?.let {
            val provider = (it.getConnectionSettings().tokenProvider.delegate as? BearerTokenProvider) ?: return@let CodeWhispererLoginType.Logout

            when (provider.state()) {
                BearerTokenAuthState.AUTHORIZED -> {
                    if (it.isSono()) {
                        CodeWhispererLoginType.Sono
                    } else {
                        CodeWhispererLoginType.SSO
                    }
                }

                BearerTokenAuthState.NEEDS_REFRESH -> CodeWhispererLoginType.Expired

                BearerTokenAuthState.NOT_AUTHENTICATED -> CodeWhispererLoginType.Logout
            }
        } ?: CodeWhispererLoginType.Logout
    }

    override fun getState(): CodeWhispererExploreActionState = CodeWhispererExploreActionState().apply {
        value.putAll(actionState.value)
        token = actionState.token
        accountlessWarnTimestamp = actionState.accountlessWarnTimestamp
        accountlessErrorTimestamp = actionState.accountlessErrorTimestamp
    }

    override fun loadState(state: CodeWhispererExploreActionState) {
        actionState.value.clear()
        actionState.token = state.token
        actionState.value.putAll(state.value)
        actionState.accountlessWarnTimestamp = state.accountlessWarnTimestamp
        actionState.accountlessErrorTimestamp = state.accountlessErrorTimestamp
    }

    companion object {
        @JvmStatic
        fun getInstance(): CodeWhispererExplorerActionManager = service()

        private val LOG = getLogger<CodeWhispererExplorerActionManager>()
    }
}
