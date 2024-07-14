package online.avogadro.mearitaskerplugin.tasker.events

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner

class MyTaskerPluginRunnerConditionNoOutputOrInputOrUpdateEvent() : TaskerPluginRunnerConditionNoInput<CameraAlarmInfo, CameraAlarmInfo>() {
    override val isEvent: Boolean get() = true

    override fun getSatisfiedCondition(context: Context, input: TaskerInput<Unit>, update: CameraAlarmInfo?): TaskerPluginResultCondition<CameraAlarmInfo> {
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}
abstract class MyTaskerPluginConfigHelperNoInput<TActionRunner : TaskerPluginRunner<Unit, CameraAlarmInfo>>(config: TaskerPluginConfig<Unit>) : TaskerPluginConfigHelperNoInput<CameraAlarmInfo, TActionRunner>(config) {
    override val inputClass = Unit::class.java
    override val outputClass = CameraAlarmInfo::class.java
}
// open class MyTaskerPluginConfigHelperEventNoOutputOrInputOrUpdate(config: TaskerPluginConfig<Unit>) : MyTaskerPluginConfigHelperNoInput<MyTaskerPluginRunnerConditionNoOutputOrInputOrUpdateEvent>(config) {
open class MyTaskerPluginConfigHelperEventNoOutputOrInputOrUpdate(config: TaskerPluginConfig<Unit>) : MyTaskerPluginConfigHelperNoInput<MyTaskerPluginRunnerConditionNoOutputOrInputOrUpdateEvent>(config) {
    override val runnerClass get() = MyTaskerPluginRunnerConditionNoOutputOrInputOrUpdateEvent::class.java
    override val inputClass = Unit::class.java
    override val outputClass = CameraAlarmInfo::class.java
}

class CameraAlarmEventHelper(config: TaskerPluginConfig<Unit>) : MyTaskerPluginConfigHelperEventNoOutputOrInputOrUpdate(config) {
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Will trigger Camera Alarm")
    }
}

class ActivityConfigCameraAlarmEvent : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CameraAlarmEventHelper(this).finishForTasker()
    }
}

fun Context.triggerTaskerEventCameraAlarm(bundle: Any?) = ActivityConfigCameraAlarmEvent::class.java.requestQuery(this, bundle)