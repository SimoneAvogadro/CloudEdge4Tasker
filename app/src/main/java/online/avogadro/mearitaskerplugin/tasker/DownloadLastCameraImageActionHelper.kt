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
import online.avogadro.mearitaskerplugin.device.CamManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigDownloadLastCameraImageBinding;

class DownloadLastCameraImageActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,DownloadLastCameraImageOutput,DownloadLastCameraImageActionRunner>(config) {
    override val runnerClass: Class<DownloadLastCameraImageActionRunner> get() = DownloadLastCameraImageActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = DownloadLastCameraImageOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Download last image from a camera")
    }
}

class ActivityConfigDownloadLastCameraImageAction : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigDownloadLastCameraImageBinding

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        // Log.d("ActivityConfigDownloadLastCameraImageAction","assignFromInput")
        binding?.editCameraID?.setText(input.regular.cameraID);
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput> get() {
        // return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput("109063372"))
        return TaskerInput<DownloadLastCameraImageInput>(DownloadLastCameraImageInput(binding?.editCameraID?.text?.toString()))
    }

    override val context get() = applicationContext
    private val taskerHelper by lazy { DownloadLastCameraImageActionHelper(this) }
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

class DownloadLastCameraImageActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput,DownloadLastCameraImageOutput>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<DownloadLastCameraImageOutput> {
        var result = ""

        val cm = CamManager(context)

        var camID = input.regular.cameraID
        if (camID=="" || camID==null || camID.toLongOrNull()==null) {
            return TaskerPluginResultErrorWithOutput(-1,"Missing CameraID parameter")
        }

        val latch = CountDownLatch(1)
        cm.getLastAlertImage(camID.toLong(), object : IDeviceAlarmMessagesCallback {
            override fun onSuccess(list: List<DeviceAlarmMessage>, cameraInfo: CameraInfo) {
                // lazy developer: I'm reusing someone's else callback :-P
                result = cameraInfo.firmID  // this is not the intended us of .firmID field but it works
                latch.countDown()
            }

            override fun onError(i: Int, s: String) {
                Log.e("getLastAlertImage Fail", "AA $i")
                result="error: "+s
                latch.countDown()
            }
        })

        latch.await(30, TimeUnit.SECONDS)

        if (result.startsWith("error:")) {
            return TaskerPluginResultErrorWithOutput(-1,result)
        } else {
            return TaskerPluginResultSucess(DownloadLastCameraImageOutput(result))
        }
    }
}