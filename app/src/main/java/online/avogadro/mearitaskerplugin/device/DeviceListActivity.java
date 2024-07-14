package online.avogadro.mearitaskerplugin.device;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.MeariDevice;
import com.meari.sdk.bean.ShareMessage;
import com.meari.sdk.callback.IDevListCallback;
import com.meari.sdk.callback.ILogoutCallback;
import com.meari.sdk.callback.IResultCallback;
import com.meari.sdk.callback.IShareMessageCallback;
import online.avogadro.mearitaskerplugin.R;
import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;
import online.avogadro.mearitaskerplugin.user.LoginActivity;

import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DeviceListAdapter adapter;
    private List<CameraInfo> deviceList;
    private ImageView imgAdd;

    private ImageView imageEnableDetection;
    private ImageView imageDisableDetection;
    private ImageView imageEnableSiren;
    private ImageView imageDisableSiren;


    private Button buttonLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        initView();

        // Connect mqtt service
        MeariUser.getInstance().connectMqttServer(MeariApplication.getInstance());

        acceptNewShares();
    }

    private void acceptNewShares() {
        MeariUser.getInstance().getShareMessage(new IShareMessageCallback() {
            @Override
            public void onSuccess(ArrayList<ShareMessage> messages) {
                for (ShareMessage s: messages) {
                    MeariUser.getInstance().dealShareMessage(s.getMsgID(), 1, new IResultCallback() {
                        @Override
                        public void onSuccess () {
                            Log.i("MqttHandler","requestShareDevice-accepted: "+s.getMsgID());
                            Toast.makeText(DeviceListActivity.this, "New device: "+s.getDeviceName() , Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError (int code, String error) {
                            Log.w("MqttHandler","requestShareDevice-failed: "+s+", "+s.getMsgID()+", -- errocode: "+code+" : "+error);
                        }
                    });
                }
            }

            @Override
            public void onError(int i, String s) {
                Log.w("tag", "Failed to accept new shares --->i: " + i + "; s: " + s);
                Toast.makeText(DeviceListActivity.this, R.string.toast_fail+" to accept new shares "+i+" s:"+s, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getData();
    }

    private void initView() {
        recyclerView = findViewById(R.id.recyclerView);
        deviceList = new ArrayList<>();
        adapter = new DeviceListAdapter(this, deviceList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);

        // imgAdd.setOnClickListener(v -> {
        //  Intent intent = new Intent(DeviceListActivity.this, AddDeviceActivity.class);
        //    startActivity(intent);
        //});

//        imgAdd = findViewById(R.id.img_add);
//        imgAdd.setOnClickListener(v -> {
//            new CamManager(DeviceListActivity.this).getLastAlertImage(109063372, new IDeviceAlarmMessagesCallback() {
//                @Override
//                public void onSuccess(List<DeviceAlarmMessage> list, CameraInfo cameraInfo) {
//                    // downloadAlertImagePreviews(list, cameraInfo);
//                    // risultato in cameraInfo.firmID
//                    Glide.with(MeariApplication.getInstance()).load(cameraInfo.getFirmID()).into(imgAdd);
//                }
//
//                @Override
//                public void onError(int i, String s) {
//                    Log.d("DevicelistFail","AA "+i);
//                }
//            });
//        });


        // === TaskerPlugin ===
        buttonLogout = findViewById(R.id.buttonLogout);
        buttonLogout.setOnClickListener(v -> {
            logout();
        });

        // ================== TaskerPlugin =================
        imageEnableDetection  = findViewById(R.id.imageEnableDetection);
        imageDisableDetection = findViewById(R.id.imageDisableDetection);
        imageEnableSiren      = findViewById(R.id.imageEnableSiren);
        imageDisableSiren     = findViewById(R.id.imageDisableSiren);

        imageEnableDetection.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Enabling cameras...", Toast.LENGTH_LONG).show();
            CamManager cm = new CamManager(DeviceListActivity.this);
            cm.enableAllCameras();
        });
        imageDisableDetection.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Disabling cameras...", Toast.LENGTH_LONG).show();
            CamManager cm = new CamManager(DeviceListActivity.this);
            cm.disableAllCameras();
        });
        imageEnableSiren.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Enabling sirens...", Toast.LENGTH_LONG).show();
            CamManager cm = new CamManager(DeviceListActivity.this);
            cm.enableAllCameraAlarms();
        });
        imageDisableSiren.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Disabling sirens...", Toast.LENGTH_LONG).show();
            CamManager cm = new CamManager(DeviceListActivity.this);
            cm.disableAllCameraAlarms();
        });

        getData();

    }



    private void logout() {
        MeariUser.getInstance().logout(new ILogoutCallback() {
            @Override
            public void onSuccess(int i) {
                afterLogout();
            }

            @Override
            public void onError(int i, String s) {
                Log.w("tag", "--->i: " + i + "; s: " + s);
                Toast.makeText(DeviceListActivity.this, R.string.toast_fail+" logout "+i+" s:"+s, Toast.LENGTH_LONG).show();
                afterLogout();
            }
        });
    }

    private void afterLogout() {
        SharedPreferencesHelper.save(DeviceListActivity.this, "username", "" );
        SharedPreferencesHelper.save(DeviceListActivity.this, "password", "" );
        Intent intent = new Intent(DeviceListActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void getData() {
        MeariUser.getInstance().getDeviceList(new IDevListCallback() {
            @Override
            public void onSuccess(MeariDevice meariDevice) {
                Log.i("tag", "--->i: ssss");
                initList(meariDevice);
            }

            @Override
            public void onError(int i, String s) {
                Log.w("tag", "--->i: " + i + "; s: " + s);
                Toast.makeText(DeviceListActivity.this, R.string.toast_fail+" getData"+i+" s: "+s, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void initList(MeariDevice meariDevice) {
        deviceList.clear();
        // deviceList.addAll(meariDevice.getIpcs());
        // deviceList.addAll(meariDevice.getDoorBells());
        // if need
        // deviceList.addAll(meariDevice.getChimes());
        // deviceList.addAll(meariDevice.getVoiceBells());
        deviceList.addAll(meariDevice.getFourthGenerations());
        deviceList.addAll(meariDevice.getBatteryCameras());
        // deviceList.addAll(meariDevice.getFlightCameras());
        // deviceList.addAll(meariDevice.getNvrs());

        adapter.notifyDataSetChanged();
    }
    

}
