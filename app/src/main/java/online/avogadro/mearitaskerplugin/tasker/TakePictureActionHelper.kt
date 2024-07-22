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
import com.meari.sdk.listener.MeariDeviceListener
import online.avogadro.mearitaskerplugin.device.CamManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigDownloadLastCameraImageBinding;

class TakePictureActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,DownloadLastCameraImageOutput,TakePictureActionRunner>(config) {
    override val runnerClass: Class<TakePictureActionRunner> get() = TakePictureActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = DownloadLastCameraImageOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(" Take high-res live picture from camera")
    }
}

class ActivityConfigTakePictureAction : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigDownloadLastCameraImageBinding

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        // Log.d("ActivityConfigTakePictureAction","assignFromInput")
        binding?.editCameraID?.setText(input.regular.cameraID);
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput> get() {
        return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput(binding?.editCameraID?.text?.toString()))
    }

    override val context get() = applicationContext
    private val taskerHelper by lazy { TakePictureActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ActivityConfigDownloadLastCameraImageBinding
        // taskerHelper.finishForTasker() // => complete config and save value
        // taskerHelper.onCreate() // => show config page
        binding =  ActivityConfigDownloadLastCameraImageBinding.inflate(layoutInflater)

        binding.buttonOK.setOnClickListener {
            // Handle button click event
            taskerHelper.finishForTasker()
        }
        setContentView(binding.root)
        taskerHelper.onCreate()
    }
}

class TakePictureActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput,DownloadLastCameraImageOutput>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<DownloadLastCameraImageOutput> {
        var result = ""

        val cm = CamManager.get(context)

        var camID = input.regular.cameraID
        if (camID=="" || camID==null || camID.toLongOrNull()==null) {
            return TaskerPluginResultErrorWithOutput(-1,"Missing CameraID parameter")
        }

        val latch = CountDownLatch(1)
        cm.takeAPicture(context, camID, object : MeariDeviceListener {
            override fun onSuccess(path: String) {
                result = path
                latch.countDown()
            }

            override fun onFailed(s: String) {
                Log.e("getLastAlertImage Fail", s)
                result="error: "+s
                latch.countDown()
            }
        })

        latch.await(90, TimeUnit.SECONDS)

        if (result==null || result.startsWith("error:")) {
            return TaskerPluginResultErrorWithOutput(-1,result)
        } else {
            return TaskerPluginResultSucess(DownloadLastCameraImageOutput(result))
        }
    }
}