package online.avogadro.mearitaskerplugin.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import online.avogadro.mearitaskerplugin.device.CamManager
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigTriggerCameraLightBinding

class TurnOnLightActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) :
	TaskerPluginConfigHelper<DownloadLastCameraImageInput, Unit, TurnOnLightActionRunner>(config) {
    override val runnerClass: Class<TurnOnLightActionRunner>
        get() = TurnOnLightActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(" ")
    }
}

class ActivityConfigTurnOnLightAction : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigTriggerCameraLightBinding

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        binding.editCameraID.setText(input.regular.cameraID)
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput>
        get() = TaskerInput(DownloadLastCameraImageInput(binding.editCameraID.text.toString()))

    override val context: Context
        get() = applicationContext

    private val taskerHelper by lazy { TurnOnLightActionHelper(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigTriggerCameraLightBinding.inflate(layoutInflater)
        
        binding.buttonOK.setOnClickListener {
            taskerHelper.finishForTasker()
        }
        setContentView(binding.root)
        taskerHelper.onCreate()
    }
}

class TurnOnLightActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput, Unit>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<Unit> {
        val cm = CamManager.get(context)
        val camID = input.regular.cameraID
        if (camID.isNullOrEmpty() || camID.toLongOrNull() == null) {
            return TaskerPluginResultErrorWithOutput(-1, "Missing or invalid CameraID parameter")
        }

        // call turnOnLight and wait for the callback (assumed synchronous for this example)
        var resultMessage = ""
        cm.turnOnLight(context, camID, object : com.meari.sdk.callback.ISetDeviceParamsCallback {
            override fun onSuccess() {
                resultMessage = "ok"
            }
            override fun onFailed(i: Int, s: String?) {
                resultMessage = "error: $s"
            }
        })
        return if (resultMessage.startsWith("error:"))
            TaskerPluginResultErrorWithOutput(-1, resultMessage)
        else
            TaskerPluginResultSucess()
    }
}
