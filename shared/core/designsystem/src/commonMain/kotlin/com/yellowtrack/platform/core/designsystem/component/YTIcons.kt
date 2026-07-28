package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Aperture
import com.composables.icons.tabler.outline.Background
import com.composables.icons.tabler.outline.Battery
import com.composables.icons.tabler.outline.Bolt
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.CalendarEvent
import com.composables.icons.tabler.outline.Camera
import com.composables.icons.tabler.outline.CircleCheck
import com.composables.icons.tabler.outline.CircleX
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.DeviceSdCard
import com.composables.icons.tabler.outline.Dots
import com.composables.icons.tabler.outline.Edit
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.LayoutDashboard
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.Phone
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Receipt
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Users

object YTIcons {
    // Navigation

    val Dashboard: ImageVector = Tabler.Outline.LayoutDashboard
    val Clients: ImageVector = Tabler.Outline.Users
    val Sessions: ImageVector = Tabler.Outline.CalendarEvent
    val Studio: ImageVector = Tabler.Outline.Camera
    val Ledger: ImageVector = Tabler.Outline.Receipt
    val Settings: ImageVector = Tabler.Outline.Settings

    // Status

    val Success: ImageVector = Tabler.Outline.CircleCheck
    val Warning: ImageVector = Tabler.Outline.AlertTriangle
    val Error: ImageVector = Tabler.Outline.CircleX
    val Info: ImageVector = Tabler.Outline.InfoCircle

    // Actions

    val Add: ImageVector = Tabler.Outline.Plus
    val Delete: ImageVector = Tabler.Outline.Trash
    val Edit: ImageVector = Tabler.Outline.Edit
    val Filter: ImageVector = Tabler.Outline.Filter
    val More: ImageVector = Tabler.Outline.Dots
    val Search: ImageVector = Tabler.Outline.Search

    // Common

    val Calendar: ImageVector = Tabler.Outline.Calendar
    val Clock: ImageVector = Tabler.Outline.Clock
    val Email: ImageVector = Tabler.Outline.Mail
    val Phone: ImageVector = Tabler.Outline.Phone
    val User: ImageVector = Tabler.Outline.User
    val Users: ImageVector = Tabler.Outline.Users

    // Photography

    val Backdrop: ImageVector = Tabler.Outline.Background
    val Battery: ImageVector = Tabler.Outline.Battery
    val Camera: ImageVector = Tabler.Outline.Camera
    val Gallery: ImageVector = Tabler.Outline.Stack
    val Lens: ImageVector = Tabler.Outline.Aperture
    val MemoryCard: ImageVector = Tabler.Outline.DeviceSdCard
    val Photo: ImageVector = Tabler.Outline.Photo
    val Speedlight: ImageVector = Tabler.Outline.Bolt
}
