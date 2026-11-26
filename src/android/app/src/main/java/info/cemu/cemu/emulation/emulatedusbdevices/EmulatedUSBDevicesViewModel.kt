package info.cemu.cemu.emulation.emulatedusbdevices

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.cemu.cemu.common.coroutines.RefreshableStateFlow
import info.cemu.cemu.nativeinterface.NativeActiveSettings
import info.cemu.cemu.nativeinterface.NativeEmulatedUSBDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class UsbDeviceEvent {
    object CreateFailed : UsbDeviceEvent()
    object LoadFailed : UsbDeviceEvent()
}

private const val SKYLANDERS_FIGURES_DIR = "emulatedUSBDevices/skylanders"
private const val SKY_FIGURE_SIZE = 0x40 * 0x10

class EmulatedUSBDevicesViewModel : ViewModel() {
    val skylanderFigures = NativeEmulatedUSBDevices.getSkylanderFigures()
    val dimensionsMiniFigures = NativeEmulatedUSBDevices.getDimensionsMiniFigures()
    val infinityFigures = NativeEmulatedUSBDevices.getInfinityFigures()

    private val _events = MutableSharedFlow<UsbDeviceEvent>()
    val events = _events.asSharedFlow()

    private fun emitEvent(usbDeviceEvent: UsbDeviceEvent) {
        viewModelScope.launch { _events.emit(usbDeviceEvent) }
    }

    private fun getFigureSlots(maxSlots: Int, slotGetter: (Int) -> String?) =
        Array(maxSlots) { slotGetter(it) }

    val skylanderSlots = RefreshableStateFlow {
        getFigureSlots(
            NativeEmulatedUSBDevices.MAX_SKYLANDERS,
            NativeEmulatedUSBDevices::getSkylandersFigureSlot
        )
    }

    val infinitySlots = RefreshableStateFlow {
        getFigureSlots(
            NativeEmulatedUSBDevices.MAX_INFINITY_SLOTS,
            NativeEmulatedUSBDevices::getInfinityFigureSlot
        )
    }

    val dimensionsSlots = RefreshableStateFlow {
        getFigureSlots(
            NativeEmulatedUSBDevices.MAX_DIMENSIONS_SLOTS,
            NativeEmulatedUSBDevices::getDimensionsFigureSlot
        )
    }

    fun clearDimensionsFigure(pad: Int, index: Int) {
        NativeEmulatedUSBDevices.clearDimensionsFigure(pad, index)
        dimensionsSlots.refresh()
    }

    fun clearInfinityFigure(slot: Int) {
        NativeEmulatedUSBDevices.clearInfinityFigure(slot)
        infinitySlots.refresh()
    }

    fun clearSkylandersFigure(slot: Int) {
        NativeEmulatedUSBDevices.clearSkylandersFigure(slot)
        skylanderSlots.refresh()
    }

    val installedSkylanderFigures =
        RefreshableStateFlow(NativeEmulatedUSBDevices::getInstalledSkylanderFigures)

    val installedDimensionMiniFigures =
        RefreshableStateFlow(NativeEmulatedUSBDevices::getInstalledDimensionsMiniFigures)

    val installedInfinityFigures =
        RefreshableStateFlow(NativeEmulatedUSBDevices::getInstalledInfinityFigures)

    private fun deleteFigure(
        installedFigure: NativeEmulatedUSBDevices.InstalledFigure, afterDelete: () -> Unit
    ) {
        NativeEmulatedUSBDevices.deleteFigure(installedFigure)
        afterDelete()
    }

    fun deleteInstalledSkylanderFigure(installedFigure: NativeEmulatedUSBDevices.InstalledFigure) =
        deleteFigure(installedFigure, installedSkylanderFigures::refresh)

    fun deleteInstalledDimensionsFigure(installedFigure: NativeEmulatedUSBDevices.InstalledFigure) =
        deleteFigure(installedFigure, installedDimensionMiniFigures::refresh)

    fun deleteInstalledInfinityFigure(installedFigure: NativeEmulatedUSBDevices.InstalledFigure) =
        deleteFigure(installedFigure, installedInfinityFigures::refresh)

    fun createSkylanderFigure(id: Int, variant: Int) {
        if (!NativeEmulatedUSBDevices.createSkylanderFigure(id, variant)) {
            emitEvent(UsbDeviceEvent.CreateFailed)
            return
        }
        installedSkylanderFigures.refresh()
    }

    fun createDimensionsMiniFigure(number: Long) {
        if (!NativeEmulatedUSBDevices.createDimensionsFigure(number)) {
            emitEvent(UsbDeviceEvent.CreateFailed)
            return
        }
        installedDimensionMiniFigures.refresh()
    }

    fun createInfinityFigure(number: Long) {
        if (!NativeEmulatedUSBDevices.createInfinityFigure(number)) {
            emitEvent(UsbDeviceEvent.CreateFailed)
            return
        }
        installedInfinityFigures.refresh()
    }

    fun loadSkylanderFigure(figure: NativeEmulatedUSBDevices.InstalledFigure, slot: Int) {
        if (!NativeEmulatedUSBDevices.loadSkylandersFigure(figure.path, slot)) {
            emitEvent(UsbDeviceEvent.LoadFailed)
            return
        }
        skylanderSlots.refresh()
    }

    /**
     * Copies a .sky file picked with the system file picker into the app's skylanders folder
     * (the native side can only open real file paths, not content:// URIs) and loads that copy
     * into [slot]. If a file with the same name is already in the folder it is reused rather than
     * overwritten, so figure progress is never replaced by an older copy.
     * Progress made in-game is saved to the copy in the app folder, not to the picked original.
     */
    fun importAndLoadSkylanderFigure(context: Context, uri: Uri, slot: Int) {
        viewModelScope.launch {
            val figure = withContext(Dispatchers.IO) { importSkylanderFigure(context, uri) }
            if (figure == null) {
                emitEvent(UsbDeviceEvent.LoadFailed)
                return@launch
            }
            installedSkylanderFigures.refresh()
            loadSkylanderFigure(figure, slot)
        }
    }

    private fun importSkylanderFigure(
        context: Context, uri: Uri
    ): NativeEmulatedUSBDevices.InstalledFigure? {
        try {
            val resolver = context.contentResolver

            val displayName = resolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

            var fileName = (displayName ?: "figure")
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .ifBlank { "figure" }
            if (!fileName.endsWith(".sky", ignoreCase = true)) {
                fileName += ".sky"
            }

            val figuresDir =
                File(NativeActiveSettings.getUserDataPath()).resolve(SKYLANDERS_FIGURES_DIR)
            figuresDir.mkdirs()
            val target = figuresDir.resolve(fileName)

            if (!target.isFile) {
                val data = ByteArray(SKY_FIGURE_SIZE + 1)
                var read = 0
                resolver.openInputStream(uri)?.use { input ->
                    while (read < data.size) {
                        val count = input.read(data, read, data.size - read)
                        if (count < 0) break
                        read += count
                    }
                } ?: return null
                // A real Skylanders dump is exactly 1KB; reject anything else.
                if (read != SKY_FIGURE_SIZE) return null
                target.writeBytes(data.copyOf(read))
            }

            return NativeEmulatedUSBDevices.InstalledFigure(
                name = target.nameWithoutExtension,
                path = target.absolutePath,
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun loadDimensionsFigure(
        figure: NativeEmulatedUSBDevices.InstalledFigure, pad: Int, index: Int
    ) {
        if (!NativeEmulatedUSBDevices.loadDimensionsFigure(figure.path, pad, index)) {
            emitEvent(UsbDeviceEvent.LoadFailed)
            return
        }
        dimensionsSlots.refresh()
    }

    fun loadInfinityFigure(figure: NativeEmulatedUSBDevices.InstalledFigure, slot: Int) {
        if (!NativeEmulatedUSBDevices.loadInfinityFigure(figure.path, slot)) {
            emitEvent(UsbDeviceEvent.LoadFailed)
            return
        }
        infinitySlots.refresh()
    }

    fun tempRemoveDimensionsFigure(index: Int) =
        NativeEmulatedUSBDevices.tempRemoveDimensionsFigure(index)

    fun cancelRemoveDimensionsFigure(index: Int) =
        NativeEmulatedUSBDevices.cancelRemoveDimensionsFigure(index)

    fun moveDimensionsFigure(pad: Int, index: Int, oldPad: Int, oldIndex: Int) {
        NativeEmulatedUSBDevices.moveDimensionsFigure(pad, index, oldPad, oldIndex)
        dimensionsSlots.refresh()
    }
}
