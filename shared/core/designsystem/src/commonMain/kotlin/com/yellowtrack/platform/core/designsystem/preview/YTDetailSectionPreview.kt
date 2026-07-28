package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTDetailSectionPreview() {
    YellowTrackTheme {
        YTDetailSection(
            title = "Contact",
            modifier =
                Modifier.padding(
                    YTTheme.spacing.large,
                ),
        ) {
            Text("Phone")

            Text("(602) 555-1234")

            Text("Email")

            Text("john@example.com")
        }
    }
}
