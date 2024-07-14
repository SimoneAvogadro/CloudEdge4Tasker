package online.avogadro.mearitaskerplugin.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.ILoginCallback;
import com.meari.sdk.callback.IResultCallback;
import online.avogadro.mearitaskerplugin.SplashActivity;
import online.avogadro.mearitaskerplugin.tasker.events.CameraAlarmInfo;
import online.avogadro.mearitaskerplugin.tasker.events.CameraAlarmRaiser;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Handle the received message here
//        if (remoteMessage.getNotification()!=null) {
//            String notificationTitle = remoteMessage.getNotification().getTitle();
//            String notificationBody = remoteMessage.getNotification().getBody();
//            Log.i("MyFirebaseMessagingSvc",notificationTitle+" "+notificationBody);
//        } else {
//            Log.w("MyFirebaseMessagingSvc","Remote message without notification");
//        }
        if (Log.isLoggable("MyFirebaseMessagingSvc", Log.DEBUG))
            Log.d("MyFirebaseMessagingSvc", remoteMessage.toString());
        CameraAlarmInfo cai = new CameraAlarmInfo();
        cai.deviceName = (String)remoteMessage.getData().getOrDefault("deviceName","<none>");
        cai.deviceID = (String)remoteMessage.getData().getOrDefault("deviceID","-1");
        CameraAlarmRaiser.INSTANCE.raiseAlarmEvent(getApplicationContext(), cai);
        // AnyNotificationReceiver.listAllDeviceMessages();
    }



    public static void startListening(Context ctx) {
//        if (1==1)   // DISABLED!!!!
//            return;

        // NEW CODE: supporto code Firebase per ricevere notifiche telecamera
        FirebaseApp.initializeApp(ctx);
        // fallisce come se non avesse inizializzato sopra
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w("MyFirebaseMessagingInit", "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();

                        // Log and toast
                        Log.d("MyFirebaseMessagingInit", "Token: "+token);
                        MeariUser.getInstance().postPushToken(1, token, new IResultCallback() {

                            @Override
                            public void onError(int i, String s) {
                                Log.e("MyFirebaseMessagingInit","onNewToken error");
                            }

                            @Override
                            public void onSuccess() {
                                Log.i("MyFirebaseMessagingInit","onNewToken success");
                            }
                        });
                    }
                });
    }

    @Override
    public void onNewToken(String str) {
        // super.onNewToken(str);
        // Looper.prepare();
        // MeariPushManager.getInstance().uploadToken(this, str, MeariPushManager.PushType.GOOGLE);
        // Looper.loop();
        // pushtype 1 ==Google

        ILoginCallback action =  new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                MeariUser.getInstance().postPushToken(1, str, new IResultCallback() {

                    @Override
                    public void onError(int i, String s) {
                        Log.e("MyFirebaseMessagingSvc","onNewToken error");
                    }

                    @Override
                    public void onSuccess() {
                        Log.i("MyFirebaseMessagingSvc","onNewToken success");
                    }
                });
            }

            @Override
            public void onError(int i, String s) {
                Log.e("MyFirebaseMessagingSvc","re-login failed");
            }
        };

        if (MeariUser.getInstance().isLogin()) {
            action.onSuccess(MeariUser.getInstance().getUserInfo());
        } else {
            SplashActivity.loginWithStoredCredentials(getApplicationContext(), action);
        }

    }
}
