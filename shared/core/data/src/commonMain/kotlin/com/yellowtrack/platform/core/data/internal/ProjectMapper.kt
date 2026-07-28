package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.database.Project as ProjectRow

internal fun ProjectRow.toDomain(): Project =
    Project(
        id = ProjectId(id),
        studioId = StudioId(studio_id),
        clientId = ClientId(client_id),
        name = name,
        serviceLine = enumOrDefault(service_line, ServiceLine.Other),
        status = enumOrDefault(status, ProjectStatus.Enquiry),
        serviceTemplateId = service_template_id?.let(::ServiceTemplateId),
        contractValue = moneyOf(contract_value_minor, contract_currency),
        enquiredAt = enquired_at.toInstantOrNull(),
        bookedAt = booked_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
