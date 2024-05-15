// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Message received from the login browser
 * property name "command", please refer to [LoginBrowser.getWebviewHTML] and Webview package [defs.d.ts]
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "command"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BrowserMessage.ToggleBrowser::class, name = "toggleBrowser"),
    JsonSubTypes.Type(value = BrowserMessage.PrepareUi::class, name = "prepareUi"),
    JsonSubTypes.Type(value = BrowserMessage.SelectConnection::class, name = "selectConnection"),
    JsonSubTypes.Type(value = BrowserMessage.LoginBuilderId::class, name = "loginBuilderId"),
    JsonSubTypes.Type(value = BrowserMessage.LoginIdC::class, name = "loginIdC"),
    JsonSubTypes.Type(value = BrowserMessage.LoginIAM::class, name = "loginIAM"),
    JsonSubTypes.Type(value = BrowserMessage.CancelLogin::class, name = "cancelLogin"),
    JsonSubTypes.Type(value = BrowserMessage.Signout::class, name = "signout"),
    JsonSubTypes.Type(value = BrowserMessage.Reauth::class, name = "reauth")
)
sealed interface BrowserMessage {

    // TODO: make it data object when 232 is not supported by us as data object syntax is not available
    object PrepareUi : BrowserMessage

    data class SelectConnection(val conectionId: String) : BrowserMessage

    object ToggleBrowser : BrowserMessage

    object LoginBuilderId : BrowserMessage

    data class LoginIdC(
        val url: String,
        val region: String,
        val feature: String
    ) : BrowserMessage

    data class LoginIAM(
        val profileName: String,
        val accessKey: String,
        val secretKey: String
    ) : BrowserMessage

    object CancelLogin : BrowserMessage

    object Signout : BrowserMessage

    object Reauth : BrowserMessage
}
