package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow

interface DeliverableRepository {
    /** What is promised on a booking, outstanding first. */
    fun observeDeliverablesForProject(projectId: ProjectId): Flow<List<Deliverable>>

    suspend fun getDeliverable(deliverableId: DeliverableId): Deliverable?

    suspend fun saveDeliverable(deliverable: Deliverable)

    suspend fun deleteDeliverable(deliverableId: DeliverableId)
}
