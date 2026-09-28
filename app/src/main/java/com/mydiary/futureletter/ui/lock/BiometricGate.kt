package com.mydiary.futureletter.ui.lock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 系统身份认证（指纹 + 锁屏密码兜底）。
 */
object BiometricGate {

    /** 设备是否已录入指纹/面容或设置了锁屏密码 */
    fun canAuthenticate(context: Context): Boolean {
        val authenticators = allowedAuthenticators()
        return BiometricManager.from(context).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /** 发起系统认证。成功走 onSuccess，失败/取消带错误信息走 onError（可空表示用户主动取消）。 */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> onError(null)
                    else -> onError(errString.toString())
                }
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁日记")
            .setSubtitle("使用指纹或锁屏密码验证身份")
            .setAllowedAuthenticators(allowedAuthenticators())
        // Android 10 及以下不允许 DEVICE_CREDENTIAL 与指纹混用，需单独提供取消按钮
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText("取消")
        }
        prompt.authenticate(builder.build())
    }

    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
}
