package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    fun observeProject(projectId: ProjectId): Flow<Project?>

    fun observeProjectsForClient(clientId: ClientId): Flow<List<Project>>

    suspend fun getProject(projectId: ProjectId): Project?

    suspend fun saveProject(project: Project)

    suspend fun deleteProject(projectId: ProjectId)
}
