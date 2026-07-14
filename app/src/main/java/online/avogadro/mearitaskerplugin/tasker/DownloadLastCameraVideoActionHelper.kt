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
import com.meari.sdk.bean.CameraInfo
import com.meari.sdk.bean.DeviceAlarmMessage
import com.meari.sdk.callback.IDeviceAlarmMessagesCallback
import online.avogadro.mearitaskerplugin.device.CamManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DownloadLastCameraVideoActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) : TaskerPluginConfigHelper<DownloadLastCameraImageInput,DownloadLastCameraVideoOutput,DownloadLastCameraVideoActionRunner>(config), HelperHolder {
    override val runnerClass: Class<DownloadLastCameraVideoActionRunner> get() = DownloadLastCameraVideoActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = DownloadLastCameraVideoOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // blurbBuilder.append("Download last alert video from a camera")
    }
}

class ActivityConfigDownloadLastCameraVideoAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { DownloadLastCameraVideoActionHelper(this) }
    override val editHint = AbstractCameraActionConfig.HINT_SINGLE
    override val helpText = AbstractCameraActionConfig.HELP_SINGLE
}

class DownloadLastCameraVideoActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput,DownloadLastCameraVideoOutput>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<DownloadLastCameraVideoOutput> {
        var result = ""

        val cm = CamManager.get(context)

        val camID = input.regular.cameraID
        if (camID=="" || camID==null || camID.toLongOrNull()==null) {
            return TaskerPluginResultErrorWithOutput(-1,"Missing CameraID parameter")
        }

        val latch = CountDownLatch(1)
        cm.getLastAlertVideo(camID.toLong(), object : IDeviceAlarmMessagesCallback {
            override fun onSuccess(list: List<DeviceAlarmMessage>, cameraInfo: CameraInfo) {
                // local video path travels in .firmID, same hack as the alert image action
                result = cameraInfo.firmID
                latch.countDown()
            }

            override fun onError(i: Int, s: String) {
                Log.e("getLastAlertVideo Fail", "$i $s")
                result = "error: $s"
                latch.countDown()
            }
        })

        // ffmpeg fetches the video segments over the network: allow more than the image
        // action's 30s, but stay below the 60s host-side timeout (see BlockingCameraAction)
        if (!latch.await(55, TimeUnit.SECONDS)) {
            return TaskerPluginResultErrorWithOutput(-2, "Timeout downloading alert video")
        }

        if (result.startsWith("error:")) {
            return TaskerPluginResultErrorWithOutput(-1,result)
        } else {
            return TaskerPluginResultSucess(DownloadLastCameraVideoOutput(result))
        }
    }
}
