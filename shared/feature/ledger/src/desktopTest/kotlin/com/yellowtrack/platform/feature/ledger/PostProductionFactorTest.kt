package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.ledger.presentation.mapper.measuredPostProductionFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning the pricing floor's oldest assumption into a measurement.
 *
 * `LedgerMapper` has assumed since 0.4.0 that an hour with a camera drags two more hours
 * of culling, editing, and admin behind it. Every minimum price the studio is shown rests
 * on that number, and it was a guess. These are the rules for replacing it with the
 * studio's own history — and, more importantly, for refusing to.
 */
class PostProductionFactorTest {
    private val projectId = ProjectId.new()

    private fun task(
        hours: Double?,
        status: PostTaskStatus = PostTaskStatus.Done,
    ) = PostProductionTask(
        id = PostProductionTaskId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        projectId = projectId,
        name = "Edit",
        kind = PostTaskKind.Edit,
        status = status,
        actualHours = hours,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    @Test
    fun `enough finished work turns the assumption into a measurement`() {
        val factor =
            measuredPostProductionFactor(
                completedTasks = listOf(task(6.0), task(4.0), task(2.0)),
                shootHours = 8.0,
            )

        // Twelve hours of post against eight of shooting: this studio runs at 1.5, not 2.
        assertEquals(1.5, factor)
    }

    @Test
    fun `one unusual job is not evidence about how a studio works`() {
        assertNull(
            measuredPostProductionFactor(completedTasks = listOf(task(40.0)), shootHours = 8.0),
            "a floor built on a single long edit is worse than one built on a stated guess",
        )
    }

    @Test
    fun `unfinished work is not counted`() {
        val factor =
            measuredPostProductionFactor(
                completedTasks =
                    listOf(
                        task(6.0),
                        task(4.0),
                        task(20.0, status = PostTaskStatus.InProgress),
                    ),
                shootHours = 8.0,
            )

        assertNull(
            factor,
            "a task half done has not overrun; counting it would flatter every open job",
        )
    }

    @Test
    fun `a finished task with no hours recorded does not count towards the measure`() {
        assertNull(
            measuredPostProductionFactor(
                completedTasks = listOf(task(6.0), task(4.0), task(null)),
                shootHours = 8.0,
            ),
            "three tasks, but only two say how long they took",
        )
    }

    @Test
    fun `nothing is measured before any shooting has happened`() {
        assertNull(
            measuredPostProductionFactor(
                completedTasks = listOf(task(6.0), task(4.0), task(2.0)),
                shootHours = 0.0,
            ),
            "dividing by no shoot hours would report an infinite factor",
        )
    }

    @Test
    fun `a studio slower than the assumption sees a higher floor, not a comfortable one`() {
        val factor =
            measuredPostProductionFactor(
                completedTasks = listOf(task(12.0), task(8.0), task(4.0)),
                shootHours = 8.0,
            )

        assertEquals(3.0, factor)
        assertTrue(
            factor!! > 2.0,
            "the point of measuring is to find out the guess was optimistic, not to confirm it",
        )
    }
}
