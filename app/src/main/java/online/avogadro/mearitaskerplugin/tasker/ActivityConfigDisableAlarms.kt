package online.avogadro.mearitaskerplugin.tasker
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.callback.ISetDeviceParamsCallback
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigDisableCameraPirBinding
import online.avogadro.mearitaskerplugin.device.CamManager

class DisableAlarmsHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput, Unit, DisableAlarmsRunner>(config) {
    override val runnerClass: Class<DisableAlarmsRunner> get() = DisableAlarmsRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // Disable PIR People detection on all cameras
        blurbBuilder.append("Disable People detection on all cameras")
    }
}

class ActivityConfigDisableAlarms : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigDisableCameraPirBinding

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        // Log.d("ActivityConfigDisableAlarms","assignFromInput")
        if (input.regular.cameraID != null)
            binding?.editCameraID?.setText(input.regular.cameraID);
        else
            binding?.editCameraID?.setText("*");
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput> get() {
        return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput(binding?.editCameraID?.text?.toString()))
    }

    override val context get() = applicationContext
    private val taskerHelper by lazy { DisableAlarmsHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =  ActivityConfigDisableCameraPirBinding.inflate(layoutInflater)

        binding.buttonOK.setOnClickListener { // Handle button click event
            taskerHelper.finishForTasker()
        }
        setContentView(binding.root)
        taskerHelper.onCreate()
    }
}

class DisableAlarmsRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput, Unit>() {
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

        cm.loginAndInitList(object : CamManager.IDoSomething {
            override fun doSomething(then: ISetDeviceParamsCallback) {
                val matched = CameraResolver.resolve(cameraID, cm.deviceList)
                if (matched.isEmpty()) {
                    result = "error: No cameras matched selector: $cameraID"
                    return
                }
                cm.disableAllCameras(matched)
            }
            override fun description() = "Disable PIR"
        })

        if (result.startsWith("error:")) {
            return TaskerPluginResultErrorWithOutput(-1, result)
        } else {
            return TaskerPluginResultSucess()
        }
    }
}