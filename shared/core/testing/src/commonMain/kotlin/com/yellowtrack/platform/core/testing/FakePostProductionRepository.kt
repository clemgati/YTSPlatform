package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.PostProductionRepository
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePostProductionRepository(
    initial: List<PostProductionTask> = emptyList(),
) : PostProductionRepository {
    private val state = MutableStateFlow(initial)

    override fun observeTasksForProject(projectId: ProjectId): Flow<List<PostProductionTask>> =
        state.map { tasks ->
            tasks
                .filter { it.projectId == projectId }
                // Matches the query: unfinished work first, then done in creation order.
                .sortedWith(compareBy({ it.status == PostTaskStatus.Done }, { it.audit.createdAt }))
        }

    override fun observeCompletedTasks(): Flow<List<PostProductionTask>> =
        state.map { tasks -> tasks.filter { it.status == PostTaskStatus.Done } }

    override suspend fun getTask(taskId: PostProductionTaskId): PostProductionTask? =
        state.value.firstOrNull { it.id == taskId }

    override suspend fun saveTask(task: PostProductionTask) {
        state.value = state.value.filterNot { it.id == task.id } + task
    }

    override suspend fun deleteTask(taskId: PostProductionTaskId) {
        state.value = state.value.filterNot { it.id == taskId }
    }
}
