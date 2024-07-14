package online.avogadro.mearitaskerplugin.tasker.events;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot;
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject;
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable;

@TaskerInputRoot
@TaskerOutputObject
public class CameraAlarmInfo {
    @TaskerInputField(key="deviceName")
    public String deviceName;

    @TaskerInputField(key="deviceID")
    public String deviceID;

    @TaskerOutputVariable(name="deviceName")
    public String getDeviceName() {
        return deviceName;
    }

    @TaskerOutputVariable(name="deviceID")
    public String getDeviceID() {
        return deviceID;
    }
}
