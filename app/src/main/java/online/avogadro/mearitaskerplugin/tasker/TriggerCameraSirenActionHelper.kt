package online.avogadro.mearitaskerplugin.tasker

import android.content.Context
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.callback.ISetDeviceParamsCallback
import online.avogadro.mearitaskerplugin.device.CamManager

class TriggerCameraSirenActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,Unit,TriggerSirenActionRunner>(config), HelperHolder {
    override val runnerClass: Class<TriggerSirenActionRunner> get() = TriggerSirenActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // blurbBuilder.append(" ")
    }
}

class ActivityConfigTriggerSirenAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { TriggerCameraSirenActionHelper(this) }
}

class TriggerSirenActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput,Unit>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<Unit> {
        var result = ""
        val cm = CamManager.get(context)
        val camID = input.regular.cameraID

        if (camID.isNullOrEmpty()) {
            return TaskerPluginResultErrorWithOutput(-1, "Missing camera selector parameter")
        }

        cm.fireSirenOnCameras(camID, object : ISetDeviceParamsCallback {
            override fun onSuccess() { result = "ok" }
            override fun onFailed(i: Int, s: String?) {
                Log.e("triggerSiren Fail", "$i $s")
                result = "error: $s"
            }
        })

        if (result.startsWith("error:"))
            return TaskerPluginResultErrorWithOutput(-1, result)
        else
            return TaskerPluginResultSucess()
    }
}
