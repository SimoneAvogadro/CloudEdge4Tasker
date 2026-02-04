package online.avogadro.mearitaskerplugin.device;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.meari.sdk.MeariIotManager;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.MeariDevice;
import com.meari.sdk.bean.ShareMessage;
import com.meari.sdk.callback.IDevListCallback;
import com.meari.sdk.callback.ILogoutCallback;
import com.meari.sdk.callback.IResultCallback;
import com.meari.sdk.callback.IShareMessageCallback;
import online.avogadro.mearitaskerplugin.R;
import online.avogadro.mearitaskerplugin.SplashActivity;
import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;
import online.avogadro.mearitaskerplugin.user.LoginActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class DeviceListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DeviceListAdapter adapter;
    private List<CameraInfo> deviceList;
    private List<CameraInfo> filteredList;

    private ImageView imageEnableDetection;
    private ImageView imageDisableDetection;
    private ImageView imageEnableSiren;
    private ImageView imageDisableSiren;
    private ImageView imageFireAlarm;
    private TabLayout tabLayout;

    private boolean prefGroupByFirstWord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);
        initView();

        // Connect mqtt service
        MeariUser.getInstance().connectMqttServer(MeariApplication.getInstance());

        // Wait for MeariIotManager before loading data
        waitForMeariInitializationThenLoadData();

        acceptNewShares();
    }

    private void waitForMeariInitializationThenLoadData() {
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable checkInitialization = new Runnable() {
            private int attempts = 0;
            private final int MAX_ATTEMPTS = 20; // 10 seconds max wait

            @Override
            public void run() {
                attempts++;
                try {
                    String accessId = MeariIotManager.getInstance().getAccessId();
                    if (accessId != null && !accessId.isEmpty()) {
                        Log.d("DeviceListActivity", "MeariIotManager ready after " + attempts + " attempts, accessId: " + accessId);
                        setControlButtonsEnabled(true);
                        getData();
                    } else if (attempts >= MAX_ATTEMPTS) {
                        Log.w("DeviceListActivity", "MeariIotManager timeout after " + attempts + " attempts, loading data anyway");
                        setControlButtonsEnabled(true);
                        getData();
                    } else {
                        Log.d("DeviceListActivity", "MeariIotManager not ready (attempt " + attempts + "/" + MAX_ATTEMPTS + "), checking again in 500ms...");
                        handler.postDelayed(this, 500);
                    }
                } catch (Exception e) {
                    Log.w("DeviceListActivity", "Error checking MeariIotManager (attempt " + attempts + "), retrying in 500ms...", e);
                    if (attempts >= MAX_ATTEMPTS) {
                        setControlButtonsEnabled(true);
                        getData();
                    } else {
                        handler.postDelayed(this, 500);
                    }
                }
            }
        };

        checkInitialization.run();
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
        readPreferences();
        adapter.setShowCameraId(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.PREF_SHOW_CAMERA_ID, true));
        rebuildTabs();
        getData();
    }

    private void readPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefGroupByFirstWord = prefs.getBoolean(SettingsActivity.PREF_GROUP_BY_FIRST_WORD, false);
    }

    private void initView() {
        recyclerView = findViewById(R.id.recyclerView);
        tabLayout = findViewById(R.id.tabLayout);
        deviceList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new DeviceListAdapter(this, filteredList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);

        // ================== Control buttons =================
        imageEnableDetection  = findViewById(R.id.imageEnableDetection);
        imageDisableDetection = findViewById(R.id.imageDisableDetection);
        imageEnableSiren      = findViewById(R.id.imageEnableSiren);
        imageDisableSiren     = findViewById(R.id.imageDisableSiren);
        imageFireAlarm        = findViewById(R.id.imageFireAlarm);

        // Initially disable all control buttons until SDK is ready
        setControlButtonsEnabled(false);

        imageEnableDetection.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Enabling cameras...", Toast.LENGTH_LONG).show();
            setAllIconsLoading(true);
            List<CameraInfo> snapshot = new ArrayList<>(filteredList);
            recyclerView.post(() -> {
                CamManager cm = CamManager.get(DeviceListActivity.this);
                cm.enableAllCameras(snapshot,
                        createPerCameraIconCallback(R.mipmap.camera_play, true, true));
            });
        });
        imageDisableDetection.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Disabling cameras...", Toast.LENGTH_LONG).show();
            setAllIconsLoading(true);
            List<CameraInfo> snapshot = new ArrayList<>(filteredList);
            recyclerView.post(() -> {
                CamManager cm = CamManager.get(DeviceListActivity.this);
                cm.disableAllCameras(snapshot,
                        createPerCameraIconCallback(R.mipmap.camera_pause, false, true));
            });
        });
        imageEnableSiren.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Enabling sirens...", Toast.LENGTH_LONG).show();
            setAllIconsLoading(false);
            List<CameraInfo> snapshot = new ArrayList<>(filteredList);
            recyclerView.post(() -> {
                CamManager cm = CamManager.get(DeviceListActivity.this);
                cm.enableAllCameraAlarms(snapshot,
                        createPerCameraIconCallback(R.mipmap.enable_siren, true, false));
            });
        });
        imageDisableSiren.setOnClickListener(v -> {
            Toast.makeText(DeviceListActivity.this, "Disabling sirens...", Toast.LENGTH_LONG).show();
            setAllIconsLoading(false);
            List<CameraInfo> snapshot = new ArrayList<>(filteredList);
            recyclerView.post(() -> {
                CamManager cm = CamManager.get(DeviceListActivity.this);
                cm.disableAllCameraAlarms(snapshot,
                        createPerCameraIconCallback(R.mipmap.disable_siren, false, false));
            });
        });
        imageFireAlarm.setOnClickListener(v -> {
            int count = filteredList.size();
            new AlertDialog.Builder(DeviceListActivity.this)
                    .setTitle("Fire siren?")
                    .setMessage("You are about to fire the siren on " + count + " cameras. Continue?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Toast.makeText(DeviceListActivity.this, "Firing siren alarms...", Toast.LENGTH_LONG).show();
                        CamManager cm = CamManager.get(DeviceListActivity.this);
                        cm.fireAllSirenAlarms(new ArrayList<>(filteredList), null);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                applyFilter((String) tab.getTag());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setControlButtonsEnabled(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;

        imageEnableDetection.setEnabled(enabled);
        imageEnableDetection.setAlpha(alpha);

        imageDisableDetection.setEnabled(enabled);
        imageDisableDetection.setAlpha(alpha);

        imageEnableSiren.setEnabled(enabled);
        imageEnableSiren.setAlpha(alpha);

        imageDisableSiren.setEnabled(enabled);
        imageDisableSiren.setAlpha(alpha);

        imageFireAlarm.setEnabled(enabled);
        imageFireAlarm.setAlpha(alpha);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                Toast.makeText(DeviceListActivity.this, "Failed to getData "+i+" s: "+s+" will re-login", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(DeviceListActivity.this, SplashActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void initList(MeariDevice meariDevice) {
        deviceList.clear();
        deviceList.addAll(meariDevice.getFourthGenerations());
        deviceList.addAll(meariDevice.getBatteryCameras());

        rebuildTabs();
    }

    private void rebuildTabs() {
        tabLayout.removeAllTabs();
        if (prefGroupByFirstWord && !deviceList.isEmpty()) {
            tabLayout.setVisibility(View.VISIBLE);

            TreeSet<String> groups = new TreeSet<>();
            for (CameraInfo ci : deviceList) {
                groups.add(firstWord(ci));
            }

            TabLayout.Tab allTab = tabLayout.newTab().setText("ALL").setTag("ALL");
            tabLayout.addTab(allTab);
            for (String g : groups) {
                tabLayout.addTab(tabLayout.newTab().setText(g).setTag(g));
            }
            // Apply current selection (ALL by default)
            applyFilter("ALL");
        } else {
            tabLayout.setVisibility(View.GONE);
            applyFilter("ALL");
        }
    }

    /**
     * Set all visible icons for the given type to alpha 0.5 (loading state).
     * @param isDetection true for PIR detection icons, false for alarm icons
     */
    private void setAllIconsLoading(boolean isDetection) {
        for (int i = 0; i < filteredList.size(); i++) {
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(i);
            if (vh instanceof DeviceListAdapter.DeviceHolder) {
                DeviceListAdapter.DeviceHolder dh = (DeviceListAdapter.DeviceHolder) vh;
                if (isDetection) {
                    dh.imgDetectionStatus.setAlpha(0.5f);
                } else {
                    dh.imgAlarmStatus.setAlpha(0.5f);
                }
            }
        }
    }

    private CamManager.ICameraOperationCallback createPerCameraIconCallback(int iconRes, boolean newState, boolean isDetection) {
        return new CamManager.ICameraOperationCallback() {
            @Override
            public void onCameraSuccess(CameraInfo cameraInfo) {
                runOnUiThread(() -> {
                    int pos = filteredList.indexOf(cameraInfo);
                    if (pos < 0) return;
                    RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(pos);
                    if (vh instanceof DeviceListAdapter.DeviceHolder) {
                        DeviceListAdapter.DeviceHolder dh = (DeviceListAdapter.DeviceHolder) vh;
                        if (isDetection) {
                            dh.imgDetectionStatus.setImageResource(iconRes);
                            dh.imgDetectionStatus.setTag(newState);
                            dh.imgDetectionStatus.setAlpha(1.0f);
                        } else {
                            dh.imgAlarmStatus.setImageResource(iconRes);
                            dh.imgAlarmStatus.setTag(newState);
                            dh.imgAlarmStatus.setAlpha(1.0f);
                        }
                    }
                });
            }

            @Override
            public void onCameraFailed(CameraInfo cameraInfo, int code, String error) {
                runOnUiThread(() -> {
                    int pos = filteredList.indexOf(cameraInfo);
                    if (pos < 0) return;
                    RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(pos);
                    if (vh instanceof DeviceListAdapter.DeviceHolder) {
                        DeviceListAdapter.DeviceHolder dh = (DeviceListAdapter.DeviceHolder) vh;
                        if (isDetection) {
                            dh.imgDetectionStatus.setAlpha(1.0f);
                        } else {
                            dh.imgAlarmStatus.setAlpha(1.0f);
                        }
                    }
                });
            }
        };
    }

    private void applyFilter(String group) {
        filteredList.clear();
        if ("ALL".equals(group)) {
            filteredList.addAll(deviceList);
        } else {
            for (CameraInfo ci : deviceList) {
                if (group.equals(firstWord(ci))) {
                    filteredList.add(ci);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static String firstWord(CameraInfo ci) {
        String name = ci.getDeviceName();
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.split(" ", 2);
        return parts[0];
    }
}
