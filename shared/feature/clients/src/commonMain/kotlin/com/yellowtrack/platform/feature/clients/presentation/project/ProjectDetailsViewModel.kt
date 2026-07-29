package com.yellowtrack.platform.feature.clients.presentation.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.PostProductionRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.toDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewPostTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One booking: its shoot days, and the post-production it drags behind them.
 *
 * This is where the hours that feed the pricing floor are actually entered — the Ledger
 * can only measure what someone has recorded.
 */
internal class ProjectDetailsViewModel(
    private val projectId: ProjectId,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    sessionRepository: SessionRepository,
    private val postProductionRepository: PostProductionRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val currency: CurrencyCode = CurrencyCode.USD,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ProjectDetailsUiState> =
        combine(
            projectRepository.observeProject(projectId),
            clientRepository.observeClients(),
            sessionRepository.observeSessionsForProject(projectId),
            postProductionRepository.observeTasksForProject(projectId),
            retryTrigger,
        ) { project, clients, sessions, tasks, _ ->
            if (project == null) {
                ProjectDetailsUiState(project = UiState.Error("This booking could not be found."))
            } else {
                ProjectDetailsUiState(
                    project =
                        UiState.Success(
                            project.toDetailsModel(
                                client = clients.firstOrNull { it.id == project.clientId },
                                sessions = sessions,
                                tasks = tasks,
                            ),
                        ),
                    currency = currency,
                )
            }
        }.catch { throwable ->
            emit(
                ProjectDetailsUiState(
                    project = UiState.Error(throwable.message ?: "Unable to load this booking."),
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProjectDetailsUiState(project = UiState.Loading),
        )

    /**
     * Adds a piece of post-production work, with what it is expected to take.
     *
     * The estimate is asked for up front rather than after the fact, because an estimate
     * written once the work is done is not an estimate — it is a memory of how long it
     * felt, and it will agree with the actual every time.
     */
    fun addTask(task: NewPostTask) {
        viewModelScope.launch {
            if (task.name.isBlank()) return@launch

            val estimated =
                when {
                    task.estimatedHours.isBlank() -> null
                    else ->
                        task.estimatedHours
                            .trim()
                            .toDoubleOrNull()
                            ?.takeIf { it > 0 } ?: return@launch
                }

            val now = clock.now()

            postProductionRepository.saveTask(
                PostProductionTask(
                    id = PostProductionTaskId.new(),
                    studioId = studioContext.studioId,
                    projectId = projectId,
                    name = task.name.trim(),
                    kind = task.kind,
                    status = PostTaskStatus.ToDo,
                    estimatedHours = estimated,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Finishes a task, recording what it really took.
     *
     * Hours are required to mark something done. A task closed without them tells the
     * pricing floor nothing, and the floor is the only reason these are tracked.
     */
    fun completeTask(
        taskId: PostProductionTaskId,
        actualHours: String,
    ) {
        viewModelScope.launch {
            val hours = actualHours.trim().toDoubleOrNull()?.takeIf { it > 0 } ?: return@launch
            val task = postProductionRepository.getTask(taskId) ?: return@launch
            val now = clock.now()

            postProductionRepository.saveTask(
                task.copy(
                    status = PostTaskStatus.Done,
                    actualHours = hours,
                    completedAt = now,
                    audit = task.audit.touched(now),
                ),
            )
        }
    }

    /** Puts a finished task back on the list, clearing what it claimed to have taken. */
    fun reopenTask(taskId: PostProductionTaskId) {
        viewModelScope.launch {
            val task = postProductionRepository.getTask(taskId) ?: return@launch
            val now = clock.now()

            postProductionRepository.saveTask(
                task.copy(
                    status = PostTaskStatus.InProgress,
                    actualHours = null,
                    completedAt = null,
                    audit = task.audit.touched(now),
                ),
            )
        }
    }

    fun deleteTask(taskId: PostProductionTaskId) {
        viewModelScope.launch { postProductionRepository.deleteTask(taskId) }
    }

    /** Corrects the booking, which is chiefly how it moves from Enquiry to Booked. */
    fun updateProject(edited: NewProject) {
        viewModelScope.launch {
            if (edited.name.isBlank()) return@launch

            val existing = projectRepository.getProject(projectId) ?: return@launch
            val contractValue =
                when {
                    edited.contractValue.isBlank() -> null
                    else -> parseMoney(edited.contractValue, currency)?.takeIf { it.isPositive } ?: return@launch
                }

            val now = clock.now()

            projectRepository.saveProject(
                existing.copy(
                    name = edited.name.trim(),
                    serviceLine = edited.serviceLine,
                    status = edited.status,
                    contractValue = contractValue,
                    // Stamped the first time it holds studio time, and never cleared: a
                    // cancellation fee is measured against the date the job was booked.
                    bookedAt = existing.bookedAt ?: now.takeIf { edited.status.isCommitted },
                    notes = edited.notes.trim().ifBlank { null },
                    audit = existing.audit.touched(now),
                ),
            )
        }
    }

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
