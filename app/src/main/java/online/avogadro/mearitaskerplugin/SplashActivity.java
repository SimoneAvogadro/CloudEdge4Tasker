package online.avogadro.mearitaskerplugin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.meari.sdk.MeariSmartSdk;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.ILoginCallback;

import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.MyFirebaseMessagingService;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;
import online.avogadro.mearitaskerplugin.device.DeviceListActivity;
import online.avogadro.mearitaskerplugin.user.LoginActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        verifyBatteryPermission();
        verifyStoragePermissions(this);
    }

    // TODO: does not seem to work
    //   1 - if opening permissions intent must not switch to home page
    //   2 - even when not switching to homepage does not seem to open the system dialog to provide the permission
    private void verifyBatteryPermission() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        String packageName = getPackageName();
        boolean isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName);

        if (!isIgnoringBatteryOptimizations) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);

            // Intent intent = new Intent(Intent.ACTION_VIEW);
            // intent.setData(Uri.parse("https://play.google.com/settings/apps/" + getPackageName()));
            // startActivity(intent);
        }
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static final String[] PERMISSIONS_STORAGE =
            {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                    ,Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ,Manifest.permission.WAKE_LOCK
            }; // ,   Manifest.permission.RECORD_AUDIO

    private void verifyStoragePermissions(Activity activity) {
        try {
            int EXTERNAL_STORAGE = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int WAKE_LOCK = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WAKE_LOCK);
            // int RECORD_AUDIO = ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO);
            if (EXTERNAL_STORAGE != PackageManager.PERMISSION_GRANTED || WAKE_LOCK!=PackageManager.PERMISSION_GRANTED) { // || RECORD_AUDIO != PackageManager.PERMISSION_GRANTED
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            } else {
                goHome();
            }
        } catch (Exception e) {
            Log.e("SplashActivity","error verifying permissions: "+e.getMessage(),e);
            Toast.makeText(this, "Failed to check permissions", Toast.LENGTH_LONG).show();
            // e.printStackTrace();
            goHome();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                goHome();
            } else {
                Log.e("SplashActivity","missing persmissions");
                goHome();
            }
        } else {
            Log.e("SplashActivity","wrong request code");
            goHome();
        }
    }

    private void goHome() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent;
////                if (MeariUser.getInstance().isLogin()) {
////                    intent = new Intent(SplashActivity.this, DeviceListActivity.class);
////                } else {
//                    intent = new Intent(SplashActivity.this, LoginActivity.class);
////                }
//                startActivity(intent);
//                finish();
                loginWithStoredCredentials(SplashActivity.this, new ILoginCallback() {
                    @Override
                    public void onSuccess(UserInfo userInfo) {
                        Toast.makeText(SplashActivity.this,"Autologin ok", Toast.LENGTH_LONG).show();
                        goToActivity(DeviceListActivity.class);
                    }

                    @Override
                    public void onError(int i, String s) {
                        Toast.makeText(SplashActivity.this, R.string.toast_fail+" autologin: "+i+" s:"+s, Toast.LENGTH_LONG).show();
                        goToActivity(LoginActivity.class);
                    }
                });
            }
        },100);
    }

    private void goToActivity(Class dest) {
        Intent intent = new Intent(this, dest);
        // no back-to-splachscreen!
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // finish();    // No more: la dobbiamo lasciare attiva
    }

    public static void loginWithStoredCredentials(Context ctx, ILoginCallback then) {
        String username = SharedPreferencesHelper.get(ctx,"username");
        String password = SharedPreferencesHelper.get(ctx,"password");
        String country = SharedPreferencesHelper.get(ctx,"country");
        String code = SharedPreferencesHelper.get(ctx,"code");

        if ("".equals(username) || "".equals(password)) {
            then.onError(-1,"No stored login data");
            return; // no stored credentials, go on with standard login
        }

        MeariSmartSdk.partnerId= MeariApplication.partnerIdS;
        MeariUser.getInstance().loginWithAccount(country, code, username, password, new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                // MyFirebaseMessagingService.startListening(SplashActivity.this);
                MyFirebaseMessagingService.startListening(ctx);
                if (then!=null)
                    then.onSuccess(userInfo);
            }

            @Override
            public void onError(int i, String s) {
                SharedPreferencesHelper.save(ctx, "username", "" );
                SharedPreferencesHelper.save(ctx, "password", "" );
                SharedPreferencesHelper.save(ctx, "country", "" );
                SharedPreferencesHelper.save(ctx, "code", "" );
                if (then!=null)
                    then.onError(i,s);
            }
        } );
    }
}
