// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
class ProjectContextController(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val encoderServer: EncoderServer = EncoderServer(project)
    private val projectContextProvider: ProjectContextProvider = ProjectContextProvider(project, encoderServer, cs)

    init {
        cs.launch {
            if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) {
                encoderServer.downloadArtifactsAndStartServer()
            }
        }

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val createdFiles = events.filterIsInstance<VFileCreateEvent>().mapNotNull { it.file?.path }
                    val deletedFiles = events.filterIsInstance<VFileDeleteEvent>().map { it.file.path }

                    updateIndex(createdFiles, ProjectContextProvider.IndexUpdateMode.ADD)
                    updateIndex(deletedFiles, ProjectContextProvider.IndexUpdateMode.REMOVE)
                }
            }
        )
    }

    fun getProjectContextIndexComplete() = projectContextProvider.isIndexComplete.get()

    fun query(prompt: String): List<RelevantDocument> {
        try {
            return projectContextProvider.query(prompt)
        } catch (e: Exception) {
            logger.warn { "error while querying for project context $e.message" }
            return emptyList()
        }
    }

    suspend fun queryInline(query: String, filePath: String): Deferred<List<InlineBm25Chunk>> {
        return withContext(coroutineContext) {
            async {
                return@async try {
                    projectContextProvider.queryInline(query, filePath)
                } catch (e: Exception) {
                    logger.warn { "error while querying inline for project context $e.message" }
                    emptyList()
                }
            }
        }
    }

    fun updateIndex(filePath: String) {
        updateIndex(listOf(filePath), ProjectContextProvider.IndexUpdateMode.UPDATE)
    }

    fun updateIndex(filePaths: List<String>, mode: ProjectContextProvider.IndexUpdateMode) {
        try {
            return projectContextProvider.updateIndex(filePaths, mode)
        } catch (e: Exception) {
            logger.warn { "error while updating index for project context $e.message" }
        }
    }

    override fun dispose() {
        Disposer.dispose(encoderServer)
        Disposer.dispose(projectContextProvider)
    }

    companion object {
        private val logger = getLogger<ProjectContextController>()
        fun getInstance(project: Project) = project.service<ProjectContextController>()
    }
}
