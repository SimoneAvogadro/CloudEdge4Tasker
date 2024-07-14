package online.avogadro.mearitaskerplugin.tasker

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

@TaskerInputRoot
class DownloadLastCameraImageInput @JvmOverloads constructor(
        @field:TaskerInputField("cameraID") var cameraID: String? = null
)