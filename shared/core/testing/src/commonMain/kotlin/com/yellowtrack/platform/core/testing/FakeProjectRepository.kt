package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProjectRepository(
    initial: List<Project> = emptyList(),
) : ProjectRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeProjects(): Flow<List<Project>> =
        state.map { projects -> failure?.let { throw it } ?: projects }

    override fun observeProject(projectId: ProjectId): Flow<Project?> =
        state.map { projects ->
            projects.firstOrNull {
                it.id ==
                    projectId
            }
        }

    override fun observeProjectsForClient(clientId: ClientId): Flow<List<Project>> =
        state.map { projects -> projects.filter { it.clientId == clientId } }

    override suspend fun getProject(projectId: ProjectId): Project? = state.value.firstOrNull { it.id == projectId }

    override suspend fun saveProject(project: Project) {
        state.value = state.value.filterNot { it.id == project.id } + project
    }

    override suspend fun deleteProject(projectId: ProjectId) {
        state.value = state.value.filterNot { it.id == projectId }
    }
}
