package online.avogadro.mearitaskerplugin.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import online.avogadro.mearitaskerplugin.device.CamManager

class BasicActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput, CameraActionOutput, BasicActionRunner>(config), HelperHolder {
    override val runnerClass: Class<BasicActionRunner> get() = BasicActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = CameraActionOutput::class.java

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

class BasicActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput, CameraActionOutput>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<CameraActionOutput> {
        val cm = CamManager.get(context)

        // backward compatibility for actions configured before the new input parameter! (it was  input: TaskerInput<Unit>)
        var cameraID: String? = ""
        try {
            cameraID = input.regular.cameraID;
        } catch (e: Exception) {
            // ignore, old config which did not come with an input
        }

        return runBlockingCameraAction("EnablePIR") { cb -> cm.enableCamerasPIR(cameraID, cb) }
    }
}
