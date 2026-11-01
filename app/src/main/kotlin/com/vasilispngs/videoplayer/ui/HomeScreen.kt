package com.vasilispngs.videoplayer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.ReportDrawnWhen
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Computer
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.Volume
import com.vasilispngs.videoplayer.data.fileName
import com.vasilispngs.videoplayer.data.smbUri
import kotlin.math.ceil

private const val MILLIS_PER_MINUTE = 60_000.0

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpen: (Screen) -> Unit, onSettings: () -> Unit) {
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val computers by viewModel.computers.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val allFilesSettings = remember(context) { context.allFilesSettings() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allFiles = Environment.isExternalStorageManager() }

    HomeContent(
        volumes = volumes,
        computers = computers,
        inProgress = inProgress,
        allFiles = allFiles,
        onAllFiles = allFilesSettings?.let { intent -> { context.open(intent) } },
        adding = adding,
        onOpen = onOpen,
        onSettings = onSettings,
        onAddComputer = viewModel::addComputer,
        onAddingHandled = viewModel::connectionHandled,
        onRemoveComputer = viewModel::removeComputer
    )
}

@Composable
internal fun HomeContent(
    volumes: List<Volume>?,
    computers: List<Computer>?,
    inProgress: List<Playback>?,
    allFiles: Boolean,
    onAllFiles: (() -> Unit)?,
    adding: Connection,
    onOpen: (Screen) -> Unit,
    onSettings: () -> Unit,
    onAddComputer: (host: String, share: String, user: String, password: String) -> Unit,
    onAddingHandled: () -> Unit,
    onRemoveComputer: (Computer) -> Unit
) {
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    val loaded = volumes != null && computers != null && inProgress != null
    val focus = rememberInitialFocus(loaded)
    val listState = rememberLazyListState()

    ReportDrawnWhen { loaded }
    LaunchedEffect(adding) {
        if (adding == Connection.Connected) {
            addDialog = false
            onAddingHandled()
        }
    }

    ListScreen(
        title = stringResource(R.string.home),
        focus = focus,
        onBack = null,
        action = { modifier -> BarAction(icon = R.drawable.ic_settings, label = stringResource(R.string.settings), onClick = onSettings, modifier = modifier) }
    ) { contentPadding, nestedScroll ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScroll)
                .focusRequester(focus)
                .focusRestorer(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            if (!loaded) return@LazyColumn
            val progress = inProgress.orEmpty()
            if (progress.isNotEmpty()) {
                item(key = "continue", contentType = HEADER) { SectionHeader(text = stringResource(R.string.continue_watching)) }
                itemsIndexed(progress, key = { _, item -> "${item.uri}#${item.size}" }, contentType = { _, _ -> ROW }) { index, item ->
                    ContinueItem(item = item, index = index, count = progress.size, onClick = { onOpen(Screen.Player(item.uri)) })
                }
            }

            val usb = volumes.orEmpty()
            val saved = computers.orEmpty()
            val warning = if (allFiles) 0 else 1
            val count = warning + usb.size + saved.size + 1
            item(key = "sources", contentType = HEADER) { SectionHeader(text = stringResource(R.string.sources)) }
            if (!allFiles) {
                item(key = "all-files", contentType = ROW) { AllFilesItem(count = count, onClick = onAllFiles) }
            }
            itemsIndexed(usb, key = { _, volume -> volume.uri.toString() }, contentType = { _, _ -> ROW }) { index, volume ->
                VolumeItem(volume = volume, index = warning + index, count = count, onClick = { onOpen(Screen.Folder(volume.uri.toString(), volume.name)) })
            }
            itemsIndexed(saved, key = { _, computer -> "smb:${computer.id}" }, contentType = { _, _ -> ROW }) { index, computer ->
                ComputerItem(
                    computer = computer,
                    index = warning + usb.size + index,
                    count = count,
                    onClick = { onOpen(Screen.Folder(smbUri(computer.host, computer.share).toString(), computer.share)) },
                    onRemove = { removing = computer.id }
                )
            }
            item(key = "add", contentType = ROW) {
                SourceItem(
                    icon = R.drawable.ic_add,
                    title = stringResource(R.string.add_computer),
                    index = count - 1,
                    count = count,
                    onClick = { addDialog = true }
                )
            }
        }
    }

    if (addDialog) {
        AddComputerDialog(
            state = adding,
            onDismiss = {
                addDialog = false
                onAddingHandled()
            },
            onAdd = onAddComputer
        )
    }

    computers?.firstOrNull { it.id == removing }?.let { computer ->
        RemoveComputerDialog(
            computer = computer,
            onDismiss = { removing = null },
            onRemove = {
                removing = null
                onRemoveComputer(computer)
            }
        )
    }
}

@Composable
private fun ContinueItem(item: Playback, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_movie), contentDescription = null) },
        supportingContent = if (item.durationMs > item.positionMs) {
            {
                val minutes = ceil((item.durationMs - item.positionMs) / MILLIS_PER_MINUTE).toInt()
                Text(text = pluralStringResource(R.plurals.minutes_left, minutes, minutes))
            }
        } else {
            null
        }
    ) {
        Text(text = item.uri.toUri().fileName)
    }
}

@Composable
private fun AllFilesItem(count: Int, onClick: (() -> Unit)?) {
    val leading = @Composable {
        Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
    val supporting = @Composable {
        Text(
            text = stringResource(
                R.string.all_files_access_description,
                stringResource(R.string.all_files_access_command, LocalContext.current.packageName)
            )
        )
    }
    val title = @Composable { Text(text = stringResource(R.string.all_files_access)) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = listItemColors(),
            leadingContent = leading,
            supportingContent = supporting,
            content = title
        )
    } else {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = listItemColors(),
            leadingContent = leading,
            supportingContent = supporting,
            content = title
        )
    }
}

@Composable
private fun VolumeItem(volume: Volume, index: Int, count: Int, onClick: () -> Unit) {
    SourceItem(icon = R.drawable.ic_usb, title = volume.name, index = index, count = count, onClick = onClick)
}

@Composable
private fun ComputerItem(computer: Computer, index: Int, count: Int, onClick: () -> Unit, onRemove: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_computer), contentDescription = null) },
        supportingContent = { Text(text = computer.host) },
        trailingContent = {
            IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.remove))
            }
        }
    ) {
        Text(text = computer.share)
    }
}

@Composable
private fun SourceItem(@DrawableRes icon: Int, title: String, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Icon(painter = painterResource(icon), contentDescription = null) }
    ) {
        Text(text = title)
    }
}

@Composable
private fun AddComputerDialog(
    state: Connection,
    onDismiss: () -> Unit,
    onAdd: (host: String, share: String, user: String, password: String) -> Unit
) {
    val address = rememberTextFieldState()
    val share = rememberTextFieldState()
    val user = rememberTextFieldState()
    val password = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val connecting = state == Connection.Connecting
    val ready = !connecting && address.text.isNotBlank() && share.text.isNotBlank()
    val submit = {
        if (ready) onAdd(address.text.trim().toString(), share.text.trim().toString(), user.text.trim().toString(), password.text.toString())
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = submit, enabled = ready, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.add_computer)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SmallSpacing)
            ) {
                ComputerField(
                    state = address,
                    label = R.string.computer_address,
                    placeholder = stringResource(R.string.computer_address_example),
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.focusRequester(focus)
                )
                ComputerField(state = share, label = R.string.computer_share)
                ComputerField(state = user, label = R.string.computer_user)
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    shape = OutlinedTextFieldDefaults.roundedShape,
                    colors = OutlinedTextFieldDefaults.tonalColors(),
                    labelPosition = TextFieldLabelPosition.Inside(),
                    label = { Text(text = stringResource(R.string.computer_password)) },
                    supportingText = when (state) {
                        Connection.Connecting -> {
                            { Text(text = stringResource(R.string.computer_connecting)) }
                        }
                        is Connection.Failed -> {
                            { Text(text = stringResource(R.string.computer_failed).withCode(state.code)) }
                        }
                        else -> null
                    },
                    isError = state is Connection.Failed,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { submit() }
                )
            }
        },
        properties = dialogProperties()
    )
}

@Composable
private fun ComputerField(
    state: TextFieldState,
    label: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        shape = OutlinedTextFieldDefaults.roundedShape,
        colors = OutlinedTextFieldDefaults.tonalColors(),
        labelPosition = TextFieldLabelPosition.Inside(),
        label = { Text(text = stringResource(label)) },
        placeholder = placeholder?.let { { Text(text = it) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine
    )
}

@Composable
private fun RemoveComputerDialog(computer: Computer, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.remove_computer)) },
        text = { Text(text = "${computer.host}\\${computer.share}") },
        properties = dialogProperties()
    )
}

private fun Context.allFilesSettings(): Intent? =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))
        .takeIf { it.resolveActivity(packageManager) != null }

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

internal const val HEADER = "header"
internal const val ROW = "row"
