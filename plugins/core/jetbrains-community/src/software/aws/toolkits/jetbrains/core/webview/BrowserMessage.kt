// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

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
    val id: String

    @JsonTypeName("prepareUi")
    data object PrepareUi : BrowserMessage {
        override val id = "prepareUi"
    }

    @JsonTypeName("selectConnection")
    data class SelectConnection(val conectionId: String) : BrowserMessage {
        override val id = "selectConnection"
    }

    @JsonTypeName("toggleBrowser")
    data object ToggleBrowser : BrowserMessage {
        override val id = "toggleBrowser"
    }

    @JsonTypeName("loginBuilderId")
    data object LoginBuilderId : BrowserMessage {
        override val id = "loginBuilderId"
    }

    @JsonTypeName("loginIdC")
    data class LoginIdC(
        val url: String,
        val region: String,
        val feature: String
    ) : BrowserMessage {
        override val id = "loginIdC"
    }

    @JsonTypeName("loginIAM")
    data class LoginIAM(
        val profileName: String,
        val accessKey: String,
        val secretKey: String
    ) : BrowserMessage {
        override val id = "loginIAM"
    }


    @JsonTypeName("cancelLogin")
    data object CancelLogin : BrowserMessage {
        override val id = "cancelLogin"
    }

    @JsonTypeName("signout")
    data object Signout : BrowserMessage {
        override val id = "signout"
    }

    @JsonTypeName("reauth")
    data object Reauth : BrowserMessage {
        override val id = "reauth"
    }
}
