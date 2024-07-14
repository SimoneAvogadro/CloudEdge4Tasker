package online.avogadro.mearitaskerplugin.user;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.meari.sdk.MeariSmartSdk;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.ILoginCallback;
import online.avogadro.mearitaskerplugin.R;

import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.MyFirebaseMessagingService;
import online.avogadro.mearitaskerplugin.device.DeviceListActivity;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;

public class LoginActivity extends AppCompatActivity {

    private EditText edtCountry, edtCode, edtAccount, edtPwd;
    private Button btnLogin, btnRegister;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initView();
    }

    private void initView() {
        edtCode = findViewById(R.id.edt_code);
        edtCountry = findViewById(R.id.edt_country);
        edtAccount = findViewById(R.id.edt_account);
        edtPwd = findViewById(R.id.edt_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        tvInfo = findViewById(R.id.tv_info);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        //  ================ TaskerPlugin  ======
        // loginWithCookie();
        // loginWithStoredCredentials();
    }

    // (CN,86)，(US, 1)
    private void login() {

        // 云云对接登录
//        String redirectionJson = "";//云云对接获取
//        String loginJson = "";//云云对接获取
//        MeariUser.getInstance().loginWithExternalData(redirectionJson, loginJson, new ILoginCallback() {
//            @Override
//            public void onSuccess(UserInfo userInfo) {
//
//            }
//
//            @Override
//            public void onError(int i, String s) {
//
//            }
//        });

        String code = edtCode.getEditableText().toString().trim();
        String country = edtCountry.getEditableText().toString().trim();
        String account = edtAccount.getEditableText().toString().trim();
        String pwd = edtPwd.getEditableText().toString().trim();
        MeariSmartSdk.partnerId= MeariApplication.partnerIdS;
        MeariUser.getInstance().loginWithAccount(country, code, account, pwd, new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                Toast.makeText(LoginActivity.this, R.string.toast_success, Toast.LENGTH_LONG).show();

                // saveLoginStatus(userInfo); // == TaskerPlugin ==
                SharedPreferencesHelper.save(LoginActivity.this, "username", account );
                SharedPreferencesHelper.save(LoginActivity.this, "password", pwd );
                SharedPreferencesHelper.save(LoginActivity.this, "country", country );
                SharedPreferencesHelper.save(LoginActivity.this, "code", code );

                // start MQTT eventlistener
                MyFirebaseMessagingService.startListening(LoginActivity.this);

                goToMain();
            }

            //     public void loginWithThird(String account, String userToken, String userName, String userIcon, String loginType, String countryCode, String phoneCode, ILoginCallback callback) {

            @Override
            public void onError(int i, String s) {
                Toast.makeText(LoginActivity.this, R.string.toast_fail, Toast.LENGTH_LONG).show();
                tvInfo.setText(i + s);
            }
        });

        // uid
//        MeariUser.getInstance().loginWithUid("CN", "86", account, new ILoginCallback() {
//            @Override
//            public void onSuccess(UserInfo userInfo) {
//                Toast.makeText(LoginActivity.this,"成功",Toast.LENGTH_LONG).show();
//                goToMain();
//            }
//
//            @Override
//            public void onError(int i, String s) {
//                Toast.makeText(LoginActivity.this,"失败",Toast.LENGTH_LONG).show();
//                tvInfo.setText(i + s);
//            }
//        });

//        MeariUser.getInstance().loginWithAccount("UC", "1", "UUID", 1, new ILoginCallback() {
//            @Override
//            public void onSuccess(UserInfo userInfo) {
//
//            }
//
//            @Override
//            public void onError(int i, String s) {
//
//            }
//        });

    }

    private void goToMain() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
        finish();
    }

    // ============= TaskerPlugin ============

    private void saveLoginStatus(UserInfo u) {
        saveLoginStatus(u.getUserAccount(), u.getUserToken(), u.getNickName(), u.getImageUrl(), ""+u.getLoginType(), u.getCountryCode(), u.getPhoneCode());
    }

    private void saveLoginStatus(String account, String token, String userName, String userIcon, String loginType, String countryCode, String phoneCode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("account", account);
        editor.putString("token",token);
        editor.putString("userName",userName);
        editor.putString("userIcon",userIcon);
        editor.putString("loginType",loginType);
        editor.putString("countryCode",countryCode);
        editor.putString("phoneCode",phoneCode);
        editor.apply();
    }

//    /**
//     * Uses previous login info to relogin without asking for password etc...
//     */
//    private void loginWithCookie() {
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        String account = prefs.getString("account", "");
//        String token = prefs.getString("token", "");
//        String userName = prefs.getString("userName", "");
//        String userIcon = prefs.getString("userIcon", "");
//        String loginType = prefs.getString("loginType", "");
//        String countryCode = prefs.getString("countryCode", "");
//        String phoneCode = prefs.getString("phoneCode", "");
//
//        MeariUser.getInstance().loginWithThird(account, token, userName, userIcon, loginType, countryCode, phoneCode, new ILoginCallback() {
//            @Override
//            public void onSuccess(UserInfo userInfo) {
//                Toast.makeText(LoginActivity.this, R.string.toast_success, Toast.LENGTH_LONG).show();
//                goToMain();
//            }
//
//            //     public void loginWithThird(String account, String userToken, String userName, String userIcon, String loginType, String countryCode, String phoneCode, ILoginCallback callback) {
//
//            @Override
//            public void onError(int i, String s) {
//                Toast.makeText(LoginActivity.this, R.string.toast_fail, Toast.LENGTH_LONG).show();
//                tvInfo.setText(i + s);
//            }
//        } );
//    }

//    private void loginWithStoredCredentials() {
//        String username = SharedPreferencesHelper.get(this,"username");
//        String password = SharedPreferencesHelper.get(this,"password");
//
//        if ("".equals(username) || "".equals(password))
//            return; // no stored credentials, go on with standard login
//
//        MeariSmartSdk.partnerId=MeariApplication.partnerIdS;
//        MeariUser.getInstance().loginWithAccount("IT", "39", username, password, new ILoginCallback() {
//            @Override
//            public void onSuccess(UserInfo userInfo) {
//                Toast.makeText(LoginActivity.this, R.string.toast_success, Toast.LENGTH_LONG).show();
//                goToMain();
//            }
//
//            //     public void loginWithThird(String account, String userToken, String userName, String userIcon, String loginType, String countryCode, String phoneCode, ILoginCallback callback) {
//
//            @Override
//            public void onError(int i, String s) {
//                Toast.makeText(LoginActivity.this, R.string.toast_fail, Toast.LENGTH_LONG).show();
//                SharedPreferencesHelper.save(LoginActivity.this, "username", "" );
//                SharedPreferencesHelper.save(LoginActivity.this, "password", "" );
//                tvInfo.setText(i + s);
//            }
//        } );
//    }

}
