package online.avogadro.mearitaskerplugin.tasker

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject
class DownloadLastCameraImageOutput(
        @get:TaskerOutputVariable("image") var image: String
) {
}