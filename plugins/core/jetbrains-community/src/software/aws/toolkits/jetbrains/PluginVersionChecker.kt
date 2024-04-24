// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import kotlinx.coroutines.CoroutineScope
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.resources.message

class PluginVersionChecker : ApplicationInitializedListener {
    override suspend fun execute(asyncScope: CoroutineScope) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            LOG.info { "Skipping due to headless environment" }
            return
        }

        val core = AwsToolkit.PLUGINS_INFO.get(AwsPlugin.CORE) ?: return
        val mismatch = AwsToolkit.PLUGINS_INFO.values.filter { it.descriptor != null && it.version != core.version }

        if (mismatch.isEmpty()) {
            return
        }

        LOG.info { "Mismatch between core version: ${core.version} and plugins: $mismatch" }

        val updated = mismatch.filter {
            val descriptor = it.descriptor as? IdeaPluginDescriptor ?: return@filter false

            tryOrNull {
                PluginUpdateManager.getUpdate(descriptor)?.install()
                LOG.info { "${descriptor.name} updated" }
                true
            } ?: false
        }

        if (updated.isNotEmpty()) {
            LOG.info { "Restarting due to forced update of plugins" }
            ApplicationManagerEx.getApplicationEx().restart(true)
            return
        }

        val notificationGroup = SingletonNotificationManager("aws.plugin.version.mismatch", NotificationType.WARNING)
        notificationGroup.notify(
            message("plugin.incompatible.title"),
            message("plugin.incompatible.message"),
            null
        ) {
            it.isImportant = true
            it.addAction(
                NotificationAction.createSimpleExpiring(message("plugin.incompatible.fix")) {
                    // try update core and disable everything else
                    val coreDescriptor = core.descriptor as? IdeaPluginDescriptor
                    tryOrNull {
                        coreDescriptor?.let { descriptor -> PluginUpdateManager.getUpdate(descriptor)?.install() }
                    }

                    PluginEnabler.getInstance().disable(
                        AwsToolkit.PLUGINS_INFO.values.mapNotNull {
                            val descriptor = it.descriptor as? IdeaPluginDescriptor
                            if (descriptor != null && descriptor != core.descriptor) {
                                descriptor
                            } else {
                                null
                            }
                        }
                    )
                }
            )
        }
    }

    companion object {
        private val LOG = getLogger<PluginVersionChecker>()
    }
}