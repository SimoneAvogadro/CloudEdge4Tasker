package online.avogadro.mearitaskerplugin.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.bean.CameraInfo
import com.meari.sdk.bean.DeviceAlarmMessage
import com.meari.sdk.callback.IDeviceAlarmMessagesCallback
import com.meari.sdk.callback.ISetDeviceParamsCallback
import online.avogadro.mearitaskerplugin.device.CamManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigDownloadLastCameraImageBinding;
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigTriggerCameraSirenBinding

class TriggerCameraSirenActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,Unit,TriggerSirenActionRunner>(config) {
    override val runnerClass: Class<TriggerSirenActionRunner> get() = TriggerSirenActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(" ")
    }
}

class ActivityConfigTriggerSirenAction : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigTriggerCameraSirenBinding

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        // Log.d("ActivityConfigTriggerSirenAction","assignFromInput")
        binding?.editCameraID?.setText(input.regular.cameraID);
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput> get() {
        // return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput("109063372"))
        return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput(binding?.editCameraID?.text?.toString()))
    }

    override val context get() = applicationContext
    private val taskerHelper by lazy { TriggerCameraSirenActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ActivityConfigDownloadLastCameraImageBinding
        // taskerHelper.finishForTasker() // => complete config and save value
        // taskerHelper.onCreate() // => show config page
        binding =  ActivityConfigTriggerCameraSirenBinding.inflate(layoutInflater)

        binding.buttonOK.setOnClickListener {
            // Handle button click event
            taskerHelper.finishForTasker()
        }
        setContentView(binding.root)
        taskerHelper.onCreate()
    }
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