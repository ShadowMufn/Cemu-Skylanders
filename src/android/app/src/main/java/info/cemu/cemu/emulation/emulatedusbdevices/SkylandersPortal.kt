package info.cemu.cemu.emulation.emulatedusbdevices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.cemu.cemu.common.ui.components.NumericUShortField
import info.cemu.cemu.common.ui.components.SearchableDropdown
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.nativeinterface.NativeEmulatedUSBDevices

@Composable
fun SkylandersPortalTab(
    skylanderSlots: Array<String?>,
    skylanderFigures: Array<NativeEmulatedUSBDevices.SkylanderFigure>,
    installedFigures: Array<NativeEmulatedUSBDevices.InstalledFigure>,
    onDeleteInstalledFigure: (NativeEmulatedUSBDevices.InstalledFigure) -> Unit,
    onLoadFigure: (NativeEmulatedUSBDevices.InstalledFigure, Int) -> Unit,
    onImportFigure: (Uri, Int) -> Unit,
    onClear: (Int) -> Unit,
    onCreate: (Int, Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showInstalledFigures by remember { mutableStateOf(false) }
    var selectForSlot by remember { mutableStateOf<Int?>(null) }
    var pickForSlot by rememberSaveable { mutableStateOf<Int?>(null) }

    // Library lists come back from the native side in directory order, so sort them here.
    val sortedFigures = remember(installedFigures) {
        installedFigures
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toTypedArray()
    }

    val figurePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pickForSlot
        pickForSlot = null
        if (uri != null && slot != null) {
            onImportFigure(uri, slot)
        }
    }

    EmulatedUSBDeviceTabContent {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showCreateDialog = true }) { Text(tr("Create")) }
            Button(onClick = { showInstalledFigures = true }) { Text(tr("Manage list")) }
        }

        for (i in 0..<NativeEmulatedUSBDevices.MAX_SKYLANDERS) {
            val slot = skylanderSlots[i]

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        text = "Skylander ${i + 1}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                    Text(text = slot ?: tr("None"), fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    pickForSlot = i
                    figurePicker.launch(arrayOf("*/*"))
                }) { Text(tr("Select")) }
                Button(onClick = { selectForSlot = i }) { Text(tr("Library")) }
                Button(enabled = slot != null, onClick = { onClear(i) }) { Text(tr("Clear")) }
            }
            HorizontalDivider()
        }
    }

    if (showCreateDialog) {
        SkylanderCreateDialog(
            skylanderFigures = skylanderFigures,
            onCreate = { id, variant -> onCreate(id.toInt(), variant.toInt()) },
            onDismiss = { showCreateDialog = false })
    }

    if (showInstalledFigures) {
        InstalledFiguresDialog(
            label = tr("Skylanders figures"),
            installedFigures = sortedFigures,
            onDelete = onDeleteInstalledFigure,
            onDismiss = { showInstalledFigures = false })
    }

    val slot = selectForSlot
    if (slot != null) {
        SelectInstalledFigureDialog(
            installedFigures = sortedFigures,
            onFigureSelected = { onLoadFigure(it, slot) },
            onDismiss = { selectForSlot = null })
    }
}

@Composable
private fun SkylanderCreateDialog(
    skylanderFigures: Array<NativeEmulatedUSBDevices.SkylanderFigure>,
    onDismiss: () -> Unit,
    onCreate: (UShort, UShort) -> Unit,
) {
    var id by remember { mutableStateOf<UShort>(0u) }
    var variant by remember { mutableStateOf<UShort>(0u) }

    FigureCreateDialog(
        title = tr("Skylander figure creator"),
        onDismiss = onDismiss,
        onCreate = { onCreate(id, variant) }) {
        SearchableDropdown(
            label = tr("Skylander figure"),
            choices = skylanderFigures,
            choiceToString = { it.name },
            onChoiceSelected = {
                id = it.id
                variant = it.variant
            })

        NumericUShortField(
            label = tr("ID"),
            value = id,
            onValueChanged = { id = it },
        )

        NumericUShortField(
            label = tr("Variant"),
            value = variant,
            onValueChanged = { variant = it },
        )
    }
}
