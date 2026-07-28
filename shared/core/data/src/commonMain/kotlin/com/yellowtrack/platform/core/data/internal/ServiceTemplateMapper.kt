package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.database.Service_template as ServiceTemplateRow

internal fun ServiceTemplateRow.toDomain(): ServiceTemplate =
    ServiceTemplate(
        id = ServiceTemplateId(id),
        studioId = StudioId(studio_id),
        name = name,
        serviceLine = enumOrDefault(service_line, ServiceLine.Other),
        defaultSessionDurationMinutes = default_session_duration_min.toInt(),
        defaultSessionCount = default_session_count.toInt(),
        basePrice = moneyOf(base_price_minor, base_price_currency),
        defaultDeliverableCount = default_deliverable_count?.toInt(),
        defaultTurnaroundDays = default_turnaround_days?.toInt(),
        defaultRevisionRounds = default_revision_rounds?.toInt(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
