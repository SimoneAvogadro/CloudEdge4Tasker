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

class DisableSirenActionHelper(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperNoInput<CameraActionOutput, DisableSirenActionRunner>(config) {
    override val runnerClass: Class<DisableSirenActionRunner> get() = DisableSirenActionRunner::class.java
    override val outputClass = CameraActionOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Disable People detection on all cameras")
    }
}

class ActivityConfigDisableSirenAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val taskerHelper by lazy { DisableSirenActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.finishForTasker()
    }
}

class DisableSirenActionRunner : TaskerPluginRunnerActionNoInput<CameraActionOutput>() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<CameraActionOutput> {
        val cm = CamManager.get(context)
        return runBlockingCameraAction("DisableSiren") { cb -> cm.disableAllCameraAlarms(cb) }
    }
}