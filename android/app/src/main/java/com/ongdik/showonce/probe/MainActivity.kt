package com.ongdik.showonce.probe

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongdik.showonce.probe.record.RecordFileStore
import com.ongdik.showonce.probe.ui.theme.ShowOnceProbeTheme

class MainActivity : ComponentActivity() {

    // Compose가 지켜보는 상태. 값이 바뀌면 화면이 다시 그려진다.
    private var serviceEnabled by mutableStateOf(false)
    private var recordSummary by mutableStateOf(RecordFileStore.Summary(fileCount = 0, totalBytes = 0))
    private var pointerState by mutableStateOf(PointerState(serviceConnected = false, pointerShowing = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShowOnceProbeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProbeScreen(
                        serviceEnabled = serviceEnabled,
                        recordSummary = recordSummary,
                        onOpenSettings = ::openAccessibilitySettings,
                        onDeleteAllRecords = ::deleteAllRecords,
                        pointerState = pointerState,
                        onShowPointer = ::showPointerAtScreenCenter,
                        onHidePointer = ::hidePointer,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    // 설정 화면에서 서비스를 켜고 돌아오면 onResume이 다시 불린다. 그때 상태를 새로 읽는다.
    override fun onResume() {
        super.onResume()
        serviceEnabled = AccessibilityStatus.isEnabled(
            enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ),
            packageName = packageName,
            className = ProbeAccessibilityService::class.java.name,
        )
        refreshRecordSummary()
        refreshPointerState()
    }

    // 서비스가 켜져 있어도 연결(onServiceConnected) 전이면 아직 포인터를 띄울 수 없다
    private fun refreshPointerState() {
        val service = ProbeAccessibilityService.connected()
        pointerState = PointerState(
            serviceConnected = service != null,
            pointerShowing = service?.isPointerShowing == true,
        )
    }

    // 고정 위치 시험이라 화면 가운데에 둔다. 10번에서는 찾은 노드의 비율 위치를 넘긴다.
    private fun showPointerAtScreenCenter() {
        ProbeAccessibilityService.connected()?.showPointer(centerRatioX = 0.5, centerRatioY = 0.5)
        refreshPointerState()
    }

    private fun hidePointer() {
        ProbeAccessibilityService.connected()?.hidePointer()
        refreshPointerState()
    }

    // 파일 몇 개의 크기만 읽는 가벼운 작업이라 화면 스레드에서 한다.
    private fun refreshRecordSummary() {
        recordSummary = RecordFileStore.summarize(ProbeAccessibilityService.recordsDirectory(this))
    }

    private fun deleteAllRecords() {
        RecordFileStore.deleteAll(ProbeAccessibilityService.recordsDirectory(this))
        refreshRecordSummary()
    }

    // 접근성은 특수 권한이라 앱 안 팝업으로 켤 수 없다. 사용자가 직접 켜도록 설정 화면으로 보낸다.
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

data class PointerState(val serviceConnected: Boolean, val pointerShowing: Boolean)

/** 바이트 수를 사람이 읽기 쉬운 크기로. 1024 단위 */
fun formatByteSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
    else -> "%.1f MB".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))
}

@Composable
fun ProbeScreen(
    serviceEnabled: Boolean,
    recordSummary: RecordFileStore.Summary,
    onOpenSettings: () -> Unit,
    onDeleteAllRecords: () -> Unit,
    pointerState: PointerState,
    onShowPointer: () -> Unit,
    onHidePointer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (serviceEnabled) R.string.status_enabled else R.string.status_disabled,
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_accessibility_settings))
        }
        if (!serviceEnabled) {
            Text(stringResource(R.string.settings_guide))
        }

        Text(
            text = stringResource(
                R.string.record_summary,
                recordSummary.fileCount,
                formatByteSize(recordSummary.totalBytes),
            ),
        )
        OutlinedButton(
            onClick = { isDeleteConfirmationVisible = true },
            enabled = recordSummary.fileCount > 0,
        ) {
            Text(stringResource(R.string.delete_all_records))
        }

        Text(
            text = stringResource(
                if (pointerState.pointerShowing) R.string.pointer_status_showing else R.string.pointer_status_hidden,
            ),
        )
        Button(onClick = onShowPointer, enabled = pointerState.serviceConnected) {
            Text(stringResource(R.string.show_pointer_at_center))
        }
        OutlinedButton(onClick = onHidePointer, enabled = pointerState.pointerShowing) {
            Text(stringResource(R.string.hide_pointer))
        }
        if (!pointerState.serviceConnected) {
            Text(stringResource(R.string.pointer_needs_service))
        }
    }

    // 실수로 누르지 않게 확인 창을 한 번 거친다.
    if (isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmationVisible = false },
            title = { Text(stringResource(R.string.delete_all_records)) },
            text = { Text(stringResource(R.string.delete_all_records_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    isDeleteConfirmationVisible = false
                    onDeleteAllRecords()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmationVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProbeScreenPreview() {
    ShowOnceProbeTheme {
        ProbeScreen(
            serviceEnabled = false,
            recordSummary = RecordFileStore.Summary(fileCount = 2, totalBytes = 34_567),
            onOpenSettings = {},
            onDeleteAllRecords = {},
            pointerState = PointerState(serviceConnected = true, pointerShowing = false),
            onShowPointer = {},
            onHidePointer = {},
        )
    }
}
