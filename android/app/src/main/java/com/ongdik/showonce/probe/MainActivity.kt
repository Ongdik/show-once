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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongdik.showonce.probe.ui.theme.ShowOnceProbeTheme

class MainActivity : ComponentActivity() {

    // Compose가 지켜보는 상태. 값이 바뀌면 화면이 다시 그려진다.
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShowOnceProbeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProbeScreen(
                        serviceEnabled = serviceEnabled,
                        onOpenSettings = ::openAccessibilitySettings,
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
    }

    // 접근성은 특수 권한이라 앱 안 팝업으로 켤 수 없다. 사용자가 직접 켜도록 설정 화면으로 보낸다.
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
fun ProbeScreen(
    serviceEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    }
}

@Preview(showBackground = true)
@Composable
fun ProbeScreenPreview() {
    ShowOnceProbeTheme {
        ProbeScreen(serviceEnabled = false, onOpenSettings = {})
    }
}
