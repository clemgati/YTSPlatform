package com.yellowtrack.platform.feature.clients.presentation.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ContractRepository
import com.yellowtrack.platform.core.data.DeliverableRepository
import com.yellowtrack.platform.core.data.ExpenseRepository
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.PostProductionRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.QuoteRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.currency
import com.yellowtrack.platform.core.data.observeCurrency
import com.yellowtrack.platform.core.data.sync.WriteFailures
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.projectRemoval
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.toDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewDeliverable
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewPostTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val sessionRepository: SessionRepository,
    private val postProductionRepository: PostProductionRepository,
    private val deliverableRepository: DeliverableRepository,
    private val contractRepository: ContractRepository,
    private val invoiceRepository: InvoiceRepository,
    private val quoteRepository: QuoteRepository,
    private val expenseRepository: ExpenseRepository,
    private val studioContext: StudioContext,
    private val studioProfileRepository: StudioProfileRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)
    private val removed = MutableStateFlow(false)

    /**
     * Grouped so the join stays within `combine`'s typed arity, the same way the Ledger
     * and the session page already do.
     */
    private data class Booking(
        val project: Project?,
        val clients: List<Client>,
        val sessions: List<Session>,
    )

    private data class Work(
        val tasks: List<PostProductionTask>,
        val deliverables: List<Deliverable>,
        val contracts: List<Contract>,
    )

    /**
     * What this booking has cost and billed. Loaded solely to answer whether the booking
     * can be removed — nothing else on this page shows it, and the page would be wrong to
     * offer removal without having looked.
     */
    private data class Money(
        val invoices: List<Invoice>,
        val quotes: List<Quote>,
        val expenses: List<Expense>,
        val mileage: List<Mileage>,
    )

    /** Why the last write did not happen. ADR 0012 made these able to fail. */
    private val writes = WriteFailures()

    val writeFailureMessage: StateFlow<String?> = writes.message

    fun dismissWriteFailure() = writes.dismiss()

    val uiState: StateFlow<ProjectDetailsUiState> =
        combine(
            combine(
                projectRepository.observeProject(projectId),
                clientRepository.observeClients(),
                sessionRepository.observeSessionsForProject(projectId),
                ::Booking,
            ),
            combine(
                postProductionRepository.observeTasksForProject(projectId),
                deliverableRepository.observeDeliverablesForProject(projectId),
                contractRepository.observeContractsForProject(projectId),
                ::Work,
            ),
            combine(
                invoiceRepository.observeInvoicesForProject(projectId),
                quoteRepository.observeQuotesForProject(projectId),
                expenseRepository.observeExpensesForProject(projectId),
                expenseRepository.observeMileageForProject(projectId),
                ::Money,
            ),
            studioProfileRepository.observeCurrency(),
            combine(retryTrigger, removed) { _, isRemoved -> isRemoved },
        ) { booking, work, money, studioCurrency, isRemoved ->
            val project = booking.project
            val clients = booking.clients
            val sessions = booking.sessions
            if (project == null) {
                ProjectDetailsUiState(
                    // Removed by this screen, rather than missing. Reporting a fault for a
                    // record the studio just asked to be rid of would be the application
                    // blaming itself for doing as it was told.
                    project =
                        if (isRemoved) {
                            UiState.Loading
                        } else {
                            UiState.Error("This booking could not be found.")
                        },
                    removed = isRemoved,
                )
            } else {
                ProjectDetailsUiState(
                    project =
                        UiState.Success(
                            project.toDetailsModel(
                                client = clients.firstOrNull { it.id == project.clientId },
                                sessions = sessions,
                                tasks = work.tasks,
                                deliverables = work.deliverables,
                                // The most recently signed contract is the one in force.
                                contract =
                                    work.contracts
                                        .filter { it.isSigned }
                                        .maxByOrNull { it.signedAt ?: it.audit.createdAt }
                                        ?: work.contracts.lastOrNull(),
                                quotes = money.quotes,
                                contracts = work.contracts,
                                now = clock.now(),
                                removal =
                                    projectRemoval(
                                        invoices = money.invoices.size,
                                        quotes = money.quotes.size,
                                        contracts = work.contracts.size,
                                        costs = money.expenses.size,
                                        journeys = money.mileage.size,
                                        shootDays = sessions.size,
                                        deliverables = work.deliverables.size,
                                        postProductionTasks = work.tasks.size,
                                    ),
                            ),
                        ),
                    currency = studioCurrency,
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
     * Takes a quote off the booking.
     *
     * Reachable only from here. The Ledger lists a quote while it is awaiting a decision
     * and drops it the moment one arrives, so an accepted quote — the one most likely to be
     * a duplicate of the quote that was actually accepted — had no screen at all.
     */
    fun deleteQuote(id: String) {
        viewModelScope.launch { quoteRepository.deleteQuote(QuoteId(id)) }
    }

    /** As [deleteQuote]. A contract leaves the Ledger once it holds its date. */
    fun deleteContract(id: String) {
        viewModelScope.launch { contractRepository.deleteContract(ContractId(id)) }
    }

    /**
     * Removes the booking, provided nothing at all is attached to it.
     *
     * Every count is taken again here rather than read from the screen. The layout was
     * drawn from a snapshot, and between drawing it and pressing the control another
     * device may have invoiced this job — which is precisely what synchronising is for. A
     * guard that lives only in the composition is a guard that holds until two people use
     * the application at once.
     */
    fun deleteProject() {
        writes.launchWrite(viewModelScope) {
            val held =
                projectRemoval(
                    invoices = invoiceRepository.observeInvoicesForProject(projectId).first().size,
                    quotes = quoteRepository.observeQuotesForProject(projectId).first().size,
                    contracts = contractRepository.observeContractsForProject(projectId).first().size,
                    costs = expenseRepository.observeExpensesForProject(projectId).first().size,
                    journeys = expenseRepository.observeMileageForProject(projectId).first().size,
                    shootDays = sessionRepository.observeSessionsForProject(projectId).first().size,
                    deliverables = deliverableRepository.observeDeliverablesForProject(projectId).first().size,
                    postProductionTasks = postProductionRepository.observeTasksForProject(projectId).first().size,
                )

            if (held !is Removal.Available) return@launchWrite

            projectRepository.deleteProject(projectId)
            removed.value = true
        }
    }

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
                    else ->
                        parseMoney(edited.contractValue, studioProfileRepository.currency())?.takeIf { it.isPositive }
                            ?: return@launch
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

    /**
     * Promises something to the client.
     *
     * No due date is asked for: it is the shoot date plus the turnaround the contract
     * already promises, and made a studio work that out by hand would be asking it to get
     * its own deadline wrong.
     */
    fun addDeliverable(deliverable: NewDeliverable) {
        viewModelScope.launch {
            if (deliverable.name.isBlank()) return@launch
            val now = clock.now()

            deliverableRepository.saveDeliverable(
                Deliverable(
                    id = DeliverableId.new(),
                    studioId = studioContext.studioId,
                    projectId = projectId,
                    name = deliverable.name.trim(),
                    kind = deliverable.kind,
                    status = DeliverableStatus.NotStarted,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Moves a deliverable along, stamping the date that goes with the state.
     *
     * Delivered and approved each carry their own moment, because "when did they get it"
     * and "when did they accept it" are different questions and a turnaround promise is
     * measured against the first.
     */
    fun setDeliverableStatus(
        deliverableId: DeliverableId,
        status: DeliverableStatus,
    ) {
        viewModelScope.launch {
            val deliverable = deliverableRepository.getDeliverable(deliverableId) ?: return@launch
            val now = clock.now()

            deliverableRepository.saveDeliverable(
                deliverable.copy(
                    status = status,
                    deliveredAt =
                        when (status) {
                            DeliverableStatus.Delivered -> deliverable.deliveredAt ?: now
                            DeliverableStatus.NotStarted -> null
                            else -> deliverable.deliveredAt
                        },
                    approvedAt = now.takeIf { status == DeliverableStatus.Approved },
                    audit = deliverable.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Records a round of changes the client asked for.
     *
     * Counted whether or not it is within the contract's allowance. A round given away for
     * free is still a round that happened, and a studio that stops counting once it is over
     * the limit loses the evidence for charging.
     */
    fun addRevisionRound(deliverableId: DeliverableId) {
        viewModelScope.launch {
            val deliverable = deliverableRepository.getDeliverable(deliverableId) ?: return@launch
            val now = clock.now()

            deliverableRepository.saveDeliverable(
                deliverable.copy(
                    revisionsUsed = deliverable.revisionsUsed + 1,
                    status = DeliverableStatus.InRevision,
                    audit = deliverable.audit.touched(now),
                ),
            )
        }
    }

    fun deleteDeliverable(deliverableId: DeliverableId) {
        viewModelScope.launch { deliverableRepository.deleteDeliverable(deliverableId) }
    }

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
