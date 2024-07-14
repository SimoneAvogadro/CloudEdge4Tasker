package online.avogadro.mearitaskerplugin.app;

import android.text.TextUtils;
import android.util.Log;

import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.FamilyMqttMsg;
import com.meari.sdk.bean.MqttMsg;
import com.meari.sdk.bean.MqttMsgType;
import com.meari.sdk.callback.IResultCallback;
import com.meari.sdk.listener.MeariDeviceListener;
import com.meari.sdk.mqtt.MqttMessageCallback;

import java.util.Locale;

public class MyMessageHandler implements MqttMessageCallback {
    @Override
    public void otherMessage(int i, String s) {
        Log.i("MqttHandler","otherMessage: "+i+", "+s);
    }

    @Override
    public void loginOnOtherDevices() {
        // todo Must deal with
        // Must handle account login on other devices
        // An account can only log in on one device at a time

        MeariUser.getInstance().disConnectMqttService();
        MeariUser.getInstance().removeUserInfo();

        MeariDeviceController controller = MeariUser.getInstance().getController();
        if (controller != null && controller.isConnected()) {
            controller.stopConnect(new MeariDeviceListener() {
                @Override
                public void onSuccess(String successMsg) {
                    Log.i("MqttHandler","loginOnOtherDevices");
                }

                @Override
                public void onFailed(String errorMsg) {
                    Log.i("MqttHandler","loginOnOtherDevices failed");
                }
            });
        }
    }

    @Override
    public void onCancelSharingDevice(String s, String s1) {
        Log.i("MqttHandler","onCancelSharingDevice: "+s+", "+s1);
    }

    @Override
    public void deviceUnbundled() {
        Log.i("MqttHandler","deviceUnbundled");
    }

    @Override
    public void onDoorbellCall(String s, boolean b) {
        Log.i("MqttHandler","onDoorbellCall: "+s+", "+b);
    }

    @Override
    public void onVoiceDoorbellCall(String s) {
        Log.i("MqttHandler","onVoiceDoorbellCall: "+s);
    }

    @Override
    public void addDeviceSuccess(String s) {
        Log.i("MqttHandler","addDeviceSuccess: "+s);
    }

    @Override
    public void addDeviceFailed(String s) {
        Log.i("MqttHandler","addDeviceFailed: "+s);
    }

    @Override
    public void addDeviceFailedUnbundled(String s) {
        Log.i("MqttHandler","addDeviceFailedUnbundled: "+s);
    }

    @Override
    public void onChimeDeviceLimit(String s) {
        Log.i("MqttHandler","onChimeDeviceLimit: "+s);
    }

    @Override
    public void ReceivedDevice(String s) {
        Log.i("MqttHandler","ReceivedDevice: "+s);
    }

    @Override
    public void requestReceivingDevice(String s, String s1, String s2) {
        Log.i("MqttHandler","requestReceivingDevice: "+s+", "+s1+", "+s2);
        MeariUser.getInstance().dealShareMessage(s2, 1, new IResultCallback() {
            @Override
            public void onSuccess () {
                Log.i("MqttHandler","requestShareDevice-accepted: "+s+", "+s1+", "+s2);
            }

            @Override
            public void onError (int code, String error) {
                Log.w("MqttHandler","requestShareDevice-failed: "+s+", "+s1+", "+s2+" -- errocode: "+code+" : "+error);
            }
        });
    }

    @Override
    public void requestReceivingDevice(String s, String s1, String s2, String s3) {
        Log.i("MqttHandler","requestReceivingDevice: "+s+", "+s1+", "+s2+", "+s3);
    }

    @Override
    public void requestShareDevice(String s, String s1, String s2) {
        Log.i("MqttHandler","requestShareDevice: "+s+", "+s1+", "+s2);
    }

    @Override
    public void requestShareDevice(String s, String s1, String s2, String s3) {
        Log.i("MqttHandler","requestShareDevice: "+s+", "+s1+", "+s2+", "+s3);
    }

    @Override
    public void onFamilyMessage(FamilyMqttMsg familyMqttMsg) {
        Log.i("MqttHandler","onFamilyMessage: "+familyMqttMsg.toString());
        // 默认家庭名称为空，更新默认家庭名称
        if (familyMqttMsg.getItemList().size() > 0) {
            for (MqttMsg.MsgItem msgItem : familyMqttMsg.getItemList()) {
                if (TextUtils.isEmpty(msgItem.name)) {
                    String name;
                    if (familyMqttMsg.getMsgId() == MqttMsgType.INVITE_JOIN_HOME) {
                        name = familyMqttMsg.getUserName();
                    } else {
                        name = MeariUser.getInstance().getUserInfo().getNickName();
                    }
                    msgItem.name = String.format(Locale.CHINA, "%s's home", name);
                }
            }
        }
        if (familyMqttMsg.getMsgId() == MqttMsgType.FAMILY_INFO_CHANGED) {
            // 家庭信息改变，刷新家庭列表
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.FAMILY_MEMBER_INFO_CHANGED) {
            // 成员信息改变，刷新成员信息
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.INVITE_JOIN_HOME) {
            // 他人邀请您加入他的家庭，弹窗并处理消息
            // MeariUser.getInstance().dealFamilyShareMessage()
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.INVITE_JOIN_HOME_SUCCESS) {
            // 您加入家庭成功，刷新家庭列表
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.APPLY_ENTER_HOME) {
            // 他人申请加入您的家庭，弹窗并处理消息
            // MeariUser.getInstance().dealFamilyShareMessage()
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.APPLY_ENTER_HOME_SUCCESS) {
            // 申请加入家庭成功，刷新家庭列表
        } else if (familyMqttMsg.getMsgId() == MqttMsgType.REMOVE_FROM_FAMILY) {
            // 您被移除家庭，刷新家庭列表
        }
    }

    @Override
    public void onCloudServiceDis() {
        Log.i("MqttHandler","onCloudServiceDis");
    }

    @Override
    public void onPermissionChanged(String s) {
        Log.i("MqttHandler","onPermissionChanged: "+s);
    }
}
