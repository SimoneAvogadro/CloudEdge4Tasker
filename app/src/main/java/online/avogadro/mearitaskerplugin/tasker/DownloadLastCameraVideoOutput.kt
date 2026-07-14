package online.avogadro.mearitaskerplugin.tasker

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject
class DownloadLastCameraVideoOutput(
        @get:TaskerOutputVariable("video") var video: String
) {
}
