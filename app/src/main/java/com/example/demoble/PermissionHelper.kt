package com.example.demoble

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "PermissionHelper"

class PermissionHelper(activityResultCaller: ActivityResultCaller) : ActivityResultCallback<Boolean> {
    private val requestPermissionLauncher = activityResultCaller.registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this
    )
    private var continuation: Continuation<Boolean>? = null

    suspend fun request(
        permission: String,
        showRequestPermissionRationale: (Activity, (Boolean) -> Unit) -> Unit,
        activity: Activity,
    ) : Boolean {
        if (continuation != null) {
            Log.d(TAG, "Another permission request is in process")
            return false
        }

        return suspendCoroutine { continuation ->
            if (ContextCompat.checkSelfPermission(
                    activity,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                continuation.resume(true)
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    showRequestPermissionRationale(activity) { granted ->
                        if (granted) {
                            requestWithContinuation(permission, continuation)
                        } else {
                            continuation.resume(false)
                        }
                    }
                } else {
                    requestWithContinuation(permission, continuation)
                }
            }
        }
    }

    private fun requestWithContinuation(permission: String, continuation: Continuation<Boolean>) {
        Log.d(TAG, "Requesting permission $permission")
        this.continuation = continuation
        requestPermissionLauncher.launch(permission)
    }

    override fun onActivityResult(isGranted: Boolean) {
        Log.d(TAG, "Permission result: $isGranted")
        continuation!!.resume(isGranted)
        continuation = null
    }
}
