package online.avogadro.mearitaskerplugin.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import online.avogadro.mearitaskerplugin.device.CamManager

class TriggerCameraSirenActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,CameraActionOutput,TriggerSirenActionRunner>(config), HelperHolder {
    override val runnerClass: Class<TriggerSirenActionRunner> get() = TriggerSirenActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = CameraActionOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // blurbBuilder.append(" ")
    }
}

class ActivityConfigTriggerSirenAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { TriggerCameraSirenActionHelper(this) }
}

class TriggerSirenActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput,CameraActionOutput>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<CameraActionOutput> {
        val cm = CamManager.get(context)
        val camID = input.regular.cameraID

        if (camID.isNullOrEmpty()) {
            return TaskerPluginResultErrorWithOutput(-1, "Missing camera selector parameter")
        }

        return runBlockingCameraAction("FireSiren") { cb -> cm.fireSirenOnCameras(camID, cb) }
    }
}
