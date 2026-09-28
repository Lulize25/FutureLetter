package com.mydiary.futureletter.ui.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 应用锁：开启后，冷启动及从后台返回均需通过系统身份认证（指纹/锁屏密码）。
 * 在 [content] 外层调用即可，未开启锁时零成本直通。
 */
@Composable
fun AppLockGate(
    content: @Composable () -> Unit
) {
    val viewModel: LockViewModel = hiltViewModel()
    // null = 设置尚未加载完成，此时显示占位页，避免日记内容在锁屏判定前闪现
    val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()
    var authenticated by rememberSaveable { mutableStateOf(false) }
    var promptShown by rememberSaveable { mutableStateOf(false) }

    // 进入后台后重新锁定，再次返回需重新验证
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                authenticated = false
                promptShown = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        // 设置加载中：空白占位
        lockEnabled == null -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {}

        // 已开启且未验证：锁屏 + 直接弹出系统认证（无需点按钮确认）
        lockEnabled == true && !authenticated -> {
            val context = LocalContext.current
            val activity = context as? FragmentActivity

            LaunchedEffect(Unit) {
                if (!promptShown && activity != null) {
                    promptShown = true
                    BiometricGate.authenticate(
                        activity = activity,
                        onSuccess = { authenticated = true },
                        onError = { } // 用户取消：留在锁屏界面，可点按钮重试
                    )
                }
            }

            LockScreen(
                canAuthenticate = activity != null && BiometricGate.canAuthenticate(context),
                onUnlock = {
                    if (activity != null) {
                        BiometricGate.authenticate(
                            activity = activity,
                            onSuccess = { authenticated = true },
                            onError = { }
                        )
                    }
                }
            )
        }

        else -> content()
    }
}

@Composable
private fun LockScreen(
    canAuthenticate: Boolean,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("日记已锁定", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (canAuthenticate) "验证指纹或锁屏密码后查看"
                else "此设备尚未录入指纹或设置锁屏密码，请先在系统中设置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (canAuthenticate) {
                Button(onClick = onUnlock) {
                    Text("指纹 / 密码解锁")
                }
            } else {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }) {
                    Text("前往系统设置")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "隐私内容仅本人可见",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}
