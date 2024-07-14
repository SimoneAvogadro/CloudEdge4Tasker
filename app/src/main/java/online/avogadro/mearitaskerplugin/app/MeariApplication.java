package online.avogadro.mearitaskerplugin.app;

import android.app.Application;

import com.meari.sdk.MeariSdk;
import com.meari.sdk.MeariSmartSdk;
// import com.meari.sdk.common.ServerType;
// import com.ppstrong.ppsplayer.meariLog;

public class MeariApplication extends Application {

    public static int partnerId = 8;
    public static String partnerIdS = "8";

    private static MeariApplication instance;

    public static MeariApplication getInstance() {
        return instance;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // MeariSdk.init(MeariApplication.this, new MyMessageHandler());
        MeariSmartSdk.partnerId=partnerIdS;
        MeariSdk.init(MeariApplication.this, partnerId, new MyMessageHandler());
        MeariSmartSdk.partnerId=partnerIdS;

        // meariLog.createlibrarylog();
        // meariLog.getInstance().setlevel(0);
        // set Debug model
        MeariSdk.getInstance().setDebug(true);
        // 设置开发环境，正式发布时去除
        // Set up the development environment and remove it when it is officially released
//            MeariSdk.getInstance().setPrivateCloudUrl(ServerType.DEVELOPMENT);

        // NEW CODE: supporto code Firebase per ricevere notifiche telecamera
        // MyFirebaseMessagingService.startListening(this);  // moved after login
    }
}
