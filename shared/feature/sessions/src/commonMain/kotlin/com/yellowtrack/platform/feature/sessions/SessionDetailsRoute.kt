package com.yellowtrack.platform.feature.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsScreen
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionDetailsRoute(
    sessionId: SessionId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the session so navigating between two builds a new ViewModel rather than
    // reusing one still bound to the previous identifier.
    val viewModel: SessionDetailsViewModel =
        koinViewModel(key = sessionId.value) { parametersOf(sessionId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The clipboard is a Compose concern and the scope is the composition's, so both stay
    // here rather than in the ViewModel. What the ViewModel owns is the document.
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var callSheetMessage by remember(sessionId) { mutableStateOf<String?>(null) }

    SessionDetailsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onUpdateSession = viewModel::updateSession,
        onMoveSession = viewModel::moveSession,
        onAddShot = viewModel::addShot,
        onAddCrew = viewModel::addCrewMember,
        onRemoveCrew = viewModel::deleteCrewMember,
        onAddRelease = viewModel::addRelease,
        onSetReleaseStatus = viewModel::setReleaseStatus,
        onRemoveRelease = viewModel::deleteRelease,
        onAddMediaCopy = viewModel::addMediaCopy,
        onVerifyMediaCopy = viewModel::markMediaCopyCheckedByHand,
        onCheckMediaCopy = viewModel::checkMediaCopy,
        onRemoveMediaCopy = viewModel::deleteMediaCopy,
        onToggleShot = viewModel::setShotCaptured,
        onDeleteShot = viewModel::deleteShot,
        onAddPackingGear = viewModel::addToPackingList,
        onSetPacked = viewModel::setPacked,
        onSetReturned = viewModel::setReturned,
        onRemovePacking = viewModel::removeFromPackingList,
        onCopyCallSheet = {
            scope.launch {
                val text = viewModel.callSheetText()

                callSheetMessage =
                    if (text == null) {
                        "This session could not be read."
                    } else {
                        clipboard.setText(AnnotatedString(text))
                        "Copied. Paste it into a message."
                    }
            }
        },
        // The message is built where the outcome is known — whether the platform offered
        // a share sheet, and where the file landed regardless.
        onSaveCallSheet = { viewModel.exportCallSheet { message -> callSheetMessage = message } },
        callSheetMessage = callSheetMessage,
        modifier = modifier,
    )
}
