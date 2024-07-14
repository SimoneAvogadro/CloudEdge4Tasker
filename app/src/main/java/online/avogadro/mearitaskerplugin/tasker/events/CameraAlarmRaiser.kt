package online.avogadro.mearitaskerplugin.tasker.events

import android.content.Context

object CameraAlarmRaiser {
    public fun raiseAlarmEvent(c: Context?, b: Any) {
        c?.triggerTaskerEventCameraAlarm(b)
    }
}
