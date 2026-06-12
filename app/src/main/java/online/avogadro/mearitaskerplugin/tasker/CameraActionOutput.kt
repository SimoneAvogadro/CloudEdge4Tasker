package online.avogadro.mearitaskerplugin.tasker

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

/**
 * Output for all camera actions. Empirically MacroDroid never completes plugin
 * actions whose output type is Unit (no output variables), while actions with a
 * real @TaskerOutputObject (e.g. Download Alert Image) complete fine - so every
 * action returns at least this summary result.
 */
@TaskerOutputObject
class CameraActionOutput(
        @get:TaskerOutputVariable("result") var result: String
) {
}
