package online.avogadro.mearitaskerplugin.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.joaomgcd.taskerpluginlibrary.SimpleResult
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.meari.sdk.callback.ISetDeviceParamsCallback
import online.avogadro.mearitaskerplugin.device.CamManager
import online.avogadro.mearitaskerplugin.databinding.ActivityConfigTriggerCameraLightBinding

class TurnOnLightActionHelper(config: TaskerPluginConfig<DownloadLastCameraImageInput>) :
	TaskerPluginConfigHelper<DownloadLastCameraImageInput, Unit, TurnOnLightActionRunner>(config), HelperHolder {
    override val runnerClass: Class<TurnOnLightActionRunner>
        get() = TurnOnLightActionRunner::class.java
    override val inputClass = DownloadLastCameraImageInput::class.java
    override val outputClass = Unit::class.java
    override fun addToStringBlurb(input: TaskerInput<DownloadLastCameraImageInput>, blurbBuilder: StringBuilder) {
        // blurbBuilder.append(" ")
    }
}

class ActivityConfigTurnOnLightAction : AbstractCameraActionConfig() {
    override val taskerHelper by lazy { TurnOnLightActionHelper(this) }
}

interface HelperHolder {
    fun finishForTasker(): SimpleResult
    fun onCreate()
}

abstract class AbstractCameraActionConfig : Activity(), TaskerPluginConfig<DownloadLastCameraImageInput> {

    private lateinit var binding: ActivityConfigTriggerCameraLightBinding
    private val cameraMap = mutableMapOf<String, String>() // Map camera name to ID

    override fun assignFromInput(input: TaskerInput<DownloadLastCameraImageInput>) {
        binding.editCameraID.setText(input.regular.cameraID)
    }

    override val inputForTasker: TaskerInput<DownloadLastCameraImageInput>
        get() = TaskerInput(DownloadLastCameraImageInput(binding.editCameraID.text.toString(), null))

    override val context: Context
        get() = applicationContext

    abstract val taskerHelper: HelperHolder

    open val editHint: String = HINT_GROUP
    open val helpText: String = HELP_GROUP

    companion object {
        const val HINT_GROUP = "ID, name, pattern (e.g. Garden*), * or %variable"
        const val HELP_GROUP = "Use \"*\" to target all cameras.\nUse a camera name or pattern with \"*\" wildcards (e.g. Garden*) to match by name.\nUse a numeric ID for exact camera ID match.\nUse %variable for Tasker/MacroDroid variable substitution."
        const val HINT_SINGLE = "Camera ID or %variable"
        const val HELP_SINGLE = "Use a numeric camera ID.\nUse %variable for Tasker/MacroDroid variable substitution."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigTriggerCameraLightBinding.inflate(layoutInflater)

        binding.editCameraID.hint = editHint
        binding.textView.text = helpText

        binding.buttonOK.setOnClickListener {
            taskerHelper.finishForTasker()
        }

        // Set up spinner
        setupCameraSpinner()

        setContentView(binding.root)
        taskerHelper.onCreate()
    }

    private fun setupCameraSpinner() {
        val cm = CamManager.get(this)
        cm.loginAndInitList(object : CamManager.IDoSomething {
            override fun doSomething(then: ISetDeviceParamsCallback) {
                // Run on UI thread since we're updating UI
                runOnUiThread {
                    val cameraNames = mutableListOf<String>()
                    cameraNames.add("-select-")
                    cameraMap.clear()
                    
                    // Populate camera map and names list
                    cm.getDeviceList().forEach { camera ->
                        val name = camera.deviceName
                        val id = camera.deviceID
                        cameraMap[name] = id
                        cameraNames.add(name)
                    }

                    // Set up spinner adapter
                    val adapter = ArrayAdapter(
                        this@AbstractCameraActionConfig,
                        android.R.layout.simple_spinner_item,
                        cameraNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerCamera.adapter = adapter

                    // Handle selection
                    binding.spinnerCamera.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val selectedName = cameraNames[position]
                            val cameraId = cameraMap[selectedName]
                            if (cameraId!=null) {
                                binding.editCameraID.setText(cameraId)
                                binding.editCameraID.setHint(selectedName)
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            // Do nothing
                        }
                    }
                }
            }

            override fun description(): String = "Load camera list"
        })
    }
}

class TurnOnLightActionRunner : TaskerPluginRunnerAction<DownloadLastCameraImageInput, Unit>() {
    override fun run(context: Context, input: TaskerInput<DownloadLastCameraImageInput>): TaskerPluginResult<Unit> {
        val cm = CamManager.get(context)
        val camID = input.regular.cameraID

        if (camID.isNullOrEmpty()) {
            return TaskerPluginResultErrorWithOutput(-1, "Missing camera selector parameter")
        }

        var resultMessage = ""
        cm.turnOnLightOnCameras(camID, object : ISetDeviceParamsCallback {
            override fun onSuccess() { resultMessage = "ok" }
            override fun onFailed(i: Int, s: String?) {
                resultMessage = "error: $s"
            }
        })

        if (resultMessage.startsWith("error:"))
            return TaskerPluginResultErrorWithOutput(-1, resultMessage)
        else
            return TaskerPluginResultSucess()
    }
}
