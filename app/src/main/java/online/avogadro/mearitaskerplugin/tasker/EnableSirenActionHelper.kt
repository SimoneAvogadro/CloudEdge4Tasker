package online.avogadro.mearitaskerplugin.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import online.avogadro.mearitaskerplugin.device.CamManager

class EnableSirenActionHelper(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperNoInput<CameraActionOutput, EnableSirenActionRunner>(config) {
    override val runnerClass: Class<EnableSirenActionRunner> get() = EnableSirenActionRunner::class.java
    override val outputClass = CameraActionOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Enable People detection on all cameras")
    }
}

class ActivityConfigEnableSirenAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val taskerHelper by lazy { EnableSirenActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.finishForTasker()
    }
}

class EnableSirenActionRunner : TaskerPluginRunnerActionNoInput<CameraActionOutput>() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<CameraActionOutput> {
        val cm = CamManager.get(context)
        return runBlockingCameraAction("EnableSiren") { cb -> cm.enableAllCameraAlarms(cb) }
    }
}