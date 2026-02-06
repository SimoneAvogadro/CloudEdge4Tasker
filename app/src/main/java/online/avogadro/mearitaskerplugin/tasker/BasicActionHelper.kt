package online.avogadro.mearitaskerplugin.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.callback.ISetDeviceParamsCallback
import online.avogadro.mearitaskerplugin.device.CamManager

class BasicActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput, Unit, BasicActionRunner>(config), HelperHolder {
    override val runnerClass: Class<BasicActionRunner> get() = BasicActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java

    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // Enable PIR People detection on all cameras
        // blurbBuilder.append("On all cameras")
    }
}

class ActivityConfigBasicAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { BasicActionHelper(this) }
    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        if (input.regular.cameraID != null) super.assignFromInput(TaskerInput(DownloadLastCameraImageInput(input.regular.cameraID)))
        else super.assignFromInput(TaskerInput(DownloadLastCameraImageInput("*")))
    }
}

class BasicActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput, Unit>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<Unit> {
        var result = ""
        val cm = CamManager.get(context)

        // backward compatibility for actions configured before the new input parameter! (it was  input: TaskerInput<Unit>)
        var cameraID: String? = ""
        try {
            cameraID = input.regular.cameraID;
        } catch (e: Exception) {
            // ignore, old config which did not come with an input
        }

        cm.enableCamerasPIR(cameraID, object : ISetDeviceParamsCallback {
            override fun onSuccess() { result = "ok" }
            override fun onFailed(i: Int, s: String?) { result = "error: $s" }
        })

        if (result.startsWith("error:"))
            return TaskerPluginResultErrorWithOutput(-1, result)
        else
            return TaskerPluginResultSucess()
    }
}
