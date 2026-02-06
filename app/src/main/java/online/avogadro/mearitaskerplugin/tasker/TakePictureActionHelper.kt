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
import com.meari.sdk.listener.MeariDeviceListener
import online.avogadro.mearitaskerplugin.device.CamManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TakePictureActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,DownloadLastCameraImageOutput,TakePictureActionRunner>(config), HelperHolder {
    override val runnerClass: Class<TakePictureActionRunner> get() = TakePictureActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = DownloadLastCameraImageOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // blurbBuilder.append(" Take high-res live picture from camera")
    }
}

class ActivityConfigTakePictureAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { TakePictureActionHelper(this) }
    override val editHint = AbstractCameraActionConfig.HINT_SINGLE
    override val helpText = AbstractCameraActionConfig.HELP_SINGLE
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
