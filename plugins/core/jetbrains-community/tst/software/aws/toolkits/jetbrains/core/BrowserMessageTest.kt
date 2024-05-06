// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.core.webview.BrowserMessage

class BrowserMessageTest {
    private lateinit var jsonObj: String
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper().registerKotlinModule()
    }

    @Test
    fun `sereialization 1`() {
        jsonObj = """
            {
                "command": "prepareUi"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual).isInstanceOf(BrowserMessage.PrepareUi::class.java)
    }

    @Test
    fun `sereialization 2`() {
        jsonObj = """
            {
                "command": "toggleBrowser"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual).isInstanceOf(BrowserMessage.ToggleBrowser::class.java)
    }

    @Test
    fun `sereialization 3`() {
        jsonObj = """
            {
                "command": "selectConnection",
                "conectionId": "foo"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual)
            .isInstanceOf(BrowserMessage.SelectConnection::class.java)
            .isEqualTo(BrowserMessage.SelectConnection("foo"))
    }

    @Test
    fun `sereialization 4`() {
        jsonObj = """
            {
                "command": "loginBuilderId"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual).isInstanceOf(BrowserMessage.LoginBuilderId::class.java)
    }

    @Test
    fun `sereialization 5`() {
        jsonObj = """
            {
                "command": "loginIdC",
                "url": "foo",
                "region": "bar",
                "feature": "baz"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual)
            .isInstanceOf(BrowserMessage.LoginIdC::class.java)
            .isEqualTo(
                BrowserMessage.LoginIdC(
                    url = "foo",
                    region = "bar",
                    feature = "baz"
                )
            )
    }

    @Test
    fun `sereialization 6`() {
        jsonObj = """
            {
                "command": "loginIAM",
                "profileName": "foo",
                "accessKey": "bar",
                "secretKey": "baz"
            }
        """.trimIndent()

        val actual = objectMapper.readValue<BrowserMessage>(jsonObj)
        assertThat(actual)
            .isInstanceOf(BrowserMessage.LoginIAM::class.java)
            .isEqualTo(
                BrowserMessage.LoginIAM(
                    profileName = "foo",
                    accessKey = "bar",
                    secretKey = "baz"
                )
            )
    }

    @Test
    fun `failure 1`() {
        assertThatThrownBy {
            jsonObj = """
            {
                "command": "loginIAM"
            }
        """.trimIndent()

            objectMapper.readValue<BrowserMessage>(jsonObj)
        }.isInstanceOf(MissingKotlinParameterException::class.java)
    }

    @Test
    fun `failure 2`() {
        assertThatThrownBy {
            jsonObj = """
            {
                "command": "loginIAM",
                "foo": "FOO"
            }
        """.trimIndent()

            objectMapper.readValue<BrowserMessage>(jsonObj)
        }.isInstanceOf(MissingKotlinParameterException::class.java)
    }
}
