package online.avogadro.mearitaskerplugin.tasker.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.DeviceAlarmMessage;
import com.meari.sdk.bean.DevicesWithNewestMsg;
import com.meari.sdk.callback.IBaseModelCallback;
import com.meari.sdk.callback.IDeviceAlarmMessagesCallback;
import online.avogadro.mearitaskerplugin.app.MeariApplication;

import java.util.List;

public class AnyNotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Enqueue work to your JobIntentService
        // MyJobIntentService.enqueueWork(context, intent);
        CameraAlarmInfo cai = new CameraAlarmInfo();
        cai.deviceName = intent.getExtras().getString("deviceName","<none>");
        cai.deviceID = intent.getExtras().getString("deviceID","-1");
        CameraAlarmRaiser.INSTANCE.raiseAlarmEvent(MeariApplication.getInstance(), cai);
    }

    public static void listAllDeviceMessages() {
        MeariUser.getInstance().getAllDeviceAlarmListWithNewestMsgNew(new IBaseModelCallback<List<DevicesWithNewestMsg>>() {
            @Override
            public void onSuccess(List<DevicesWithNewestMsg> list) {
                for (DevicesWithNewestMsg d: list) {
                    listMessagesForDevice(d.deviceID);
                    Log.d("AnyNotificationReceiver", "Device: "+d.deviceName+" id:"+d.deviceID);
                }
            }

            @Override
            public void onFailed(int i, String s) {
                Log.e("AnyNotificationReceiver", "AlarmImageExtract error: "+i+" s: "+s);
            }
        });

    }
    private static void listMessagesForDevice(long deviceID){
        MeariUser.getInstance().getAlarmMessagesForDev(deviceID, new IDeviceAlarmMessagesCallback() {
            @Override
            public void onSuccess(List<DeviceAlarmMessage> list, CameraInfo cameraInfo) {
                if (list.size()>0) {
                    String imageURL = list.get(0).getImageUrl();
                    Log.d("AnyNotificationReceiver", "Image: "+imageURL);
                }
            }

            @Override
            public void onError(int i, String s) {
                Log.e("AnyNotificationReceiver", "AlarmImageExtract error: "+i+" s: "+s);
            }
        });
    }
}