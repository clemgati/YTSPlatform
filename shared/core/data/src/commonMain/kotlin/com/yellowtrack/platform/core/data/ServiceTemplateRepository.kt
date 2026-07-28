package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlinx.coroutines.flow.Flow

interface ServiceTemplateRepository {
    fun observeTemplates(): Flow<List<ServiceTemplate>>

    suspend fun getTemplate(id: ServiceTemplateId): ServiceTemplate?

    suspend fun saveTemplate(template: ServiceTemplate)

    suspend fun deleteTemplate(id: ServiceTemplateId)

    /** Installs the studio's starting templates the first time the app runs. */
    suspend fun seedDefaultsIfEmpty()
}
