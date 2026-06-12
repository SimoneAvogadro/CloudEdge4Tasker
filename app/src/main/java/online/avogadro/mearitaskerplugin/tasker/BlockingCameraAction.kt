package online.avogadro.mearitaskerplugin.tasker

import android.util.Log
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.callback.ISetDeviceParamsCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CE4T"

/**
 * Runs an async CamManager operation and blocks until its callback fires (or the
 * timeout elapses), so the Tasker/MacroDroid completion signal is sent only when the
 * operation has really finished, and carries a real %result output variable
 * (MacroDroid never completes actions with no output variables, see CameraActionOutput).
 *
 * The timeout must stay below the timeout requested from the host at config time
 * (60s, TaskerPluginConfigHelper.timeoutSeconds).
 */
internal fun runBlockingCameraAction(
    actionName: String,
    timeoutSeconds: Long = 45,
    action: (ISetDeviceParamsCallback) -> Unit
): TaskerPluginResult<CameraActionOutput> {
    val start = System.currentTimeMillis()
    val latch = CountDownLatch(1)
    val error = AtomicReference<String>()
    Log.i(TAG, "$actionName: dispatching camera operation")
    action(object : ISetDeviceParamsCallback {
        override fun onSuccess() {
            Log.i(TAG, "$actionName: operation completed OK after ${System.currentTimeMillis() - start}ms")
            latch.countDown()
        }

        override fun onFailed(i: Int, s: String?) {
            Log.w(TAG, "$actionName: operation failed after ${System.currentTimeMillis() - start}ms: $i $s")
            error.compareAndSet(null, "error $i: $s")
            latch.countDown()
        }
    })
    try {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            Log.w(TAG, "$actionName: TIMEOUT after ${timeoutSeconds}s, returning error to host")
            return TaskerPluginResultErrorWithOutput(-2, "Timeout: cameras did not answer within ${timeoutSeconds}s")
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        return TaskerPluginResultErrorWithOutput(-3, "Interrupted while waiting for cameras")
    }
    error.get()?.let {
        Log.i(TAG, "$actionName: returning ERROR to host: $it")
        return TaskerPluginResultErrorWithOutput(-1, it)
    }
    Log.i(TAG, "$actionName: returning SUCCESS to host (library will now signalFinish)")
    return TaskerPluginResultSucess(CameraActionOutput("OK"))
}
