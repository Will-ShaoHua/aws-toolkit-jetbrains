// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderExtension
import software.aws.toolkits.jetbrains.core.webview.BearerLoginHandler

@ExtendWith(MockKExtension::class)
class LoginUtilsTest {
    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @JvmField
    @RegisterExtension
    val mockRegionProvider = MockRegionProviderExtension()

    private var testUrl = aString()

    private lateinit var sut: Login
    private lateinit var configFacade: ConfigFilesFacade

    private val project: Project
        get() = projectExtension.project

    @BeforeEach
    fun setUp() {
        testUrl = aString()
        configFacade = mockk<ConfigFilesFacade>(relaxed = true)
    }

    @Test
    fun `login awsId successfully should run handler onSuccess`() {
        mockkStatic(::loginSso)
        val scopes = listOf(aString())
        val mockReturned = mockk<AwsBearerTokenConnection>()
        every { loginSso(project, SONO_URL, SONO_REGION, scopes, any()) } returns mockReturned

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.BuilderId(
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            }
        )

        val actual = sut.login(project)
        verify {
            loginSso(
                project,
                SONO_URL,
                SONO_REGION,
                scopes,
                any()
            )
        }
        assertThat(actual).isEqualTo(mockReturned)
        assertThat(cntOfSuccess).isEqualTo(1)
        assertThat(cntOfError).isEqualTo(0)
    }

    @Test
    fun `login awsId fail should run handler onError and return null`() {
        mockkStatic(::loginSso)
        val scopes = listOf(aString())
        every { loginSso(project, SONO_URL, SONO_REGION, scopes, any()) } throws InvalidGrantException.create("test", Exception())

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.BuilderId(
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            }
        )

        val actual = sut.login(project)
        verify {
            loginSso(
                project,
                SONO_URL,
                SONO_REGION,
                scopes,
                any()
            )
        }
        assertThat(actual).isEqualTo(null)
        assertThat(cntOfSuccess).isEqualTo(0)
        assertThat(cntOfError).isEqualTo(1)
    }

    @Test
    fun `login idc succeed should run handler onSuccess`(@TestDisposable disposable: Disposable) {
        mockkStatic(::authAndUpdateConfig)
        val url = "https://fooBarBaz.awsapps.com/start"
        val scopes = listOf(aString())
        val region = mockRegionProvider.defaultRegion()

        val mockReturned = mockk<AwsBearerTokenConnection>()
        every { authAndUpdateConfig(project, any(), any(), any(), any()) } returns mockReturned

        val connectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposable)

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.IdC(
            url,
            region,
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            },
            configFacade
        )

        val actual = sut.login(project)
        verify {
            authAndUpdateConfig(
                project,
                UserConfigSsoSessionProfile("fooBarBaz", region.id, url, scopes),
                configFacade,
                any(),
                any()
            )

            connectionManager.switchConnection(mockReturned)
        }
        assertThat(actual).isEqualTo(mockReturned)
        assertThat(cntOfSuccess).isEqualTo(1)
        assertThat(cntOfError).isEqualTo(0)
    }

    @Test
    fun `login idc fail should run handler onError and return null`(@TestDisposable disposable: Disposable) {
        mockkStatic(::authAndUpdateConfig)
        val url = "https://fooBarBaz.awsapps.com/start"
        val scopes = listOf(aString())
        val region = mockRegionProvider.defaultRegion()

        every { authAndUpdateConfig(project, any(), any(), any(), any()) } throws InvalidGrantException.create("test", Exception())

        val connectionManager = mockk<ToolkitConnectionManager>(relaxed = true)
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposable)

        var cntOfSuccess = 0
        var cntOfError = 0

        sut = Login.IdC(
            url,
            region,
            scopes,
            object : BearerLoginHandler {
                override fun onSuccess(connection: ToolkitConnection) {
                    cntOfSuccess++
                }

                override fun onError(e: Exception) {
                    cntOfError++
                }
            },
            configFacade
        )

        val actual = sut.login(project)
        verify {
            authAndUpdateConfig(
                project,
                UserConfigSsoSessionProfile("fooBarBaz", region.id, url, scopes),
                configFacade,
                any(),
                any()
            )
        }

        verify(inverse = true) {
            connectionManager.switchConnection(any())
        }

        assertThat(actual).isEqualTo(null)
        assertThat(cntOfSuccess).isEqualTo(0)
        assertThat(cntOfError).isEqualTo(1)
    }
}
