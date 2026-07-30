package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow

interface PostProductionRepository {
    /** Work on one booking, unfinished first. */
    fun observeTasksForProject(projectId: ProjectId): Flow<List<PostProductionTask>>

    /**
     * Every finished task across the studio.
     *
     * This is what lets the pricing floor stop guessing at how long post-production takes
     * and start measuring it.
     */
    fun observeCompletedTasks(): Flow<List<PostProductionTask>>

    suspend fun getTask(taskId: PostProductionTaskId): PostProductionTask?

    suspend fun saveTask(task: PostProductionTask)

    suspend fun deleteTask(taskId: PostProductionTaskId)
}
