package com.yellowtrack.platform.feature.dashboard.presentation.extension

import com.yellowtrack.platform.core.designsystem.component.YTStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatusItem

internal val DashboardStudioStatusItem.status: YTStatus
    get() =
        if (ready) {
            YTStatus.Success
        } else {
            YTStatus.Warning
        }

internal val DashboardStudioStatusItem.statusContentDescription: String
    get() =
        if (ready) {
            "$title is ready"
        } else {
            "$title needs attention"
        }
