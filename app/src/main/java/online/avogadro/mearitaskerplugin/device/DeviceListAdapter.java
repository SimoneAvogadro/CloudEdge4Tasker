package online.avogadro.mearitaskerplugin.device;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.DeviceParams;
import com.meari.sdk.callback.IGetDeviceParamsCallback;
import com.meari.sdk.callback.ISetDeviceParamsCallback;

import online.avogadro.mearitaskerplugin.R;

import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceHolder> {

    private Context context;
    private List<CameraInfo> deviceList;

    public DeviceListAdapter(Context context, List<CameraInfo> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
    }

    @NonNull
    @Override
    public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_device_list, parent, false);
        return new DeviceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceHolder holder, int position) {
        CameraInfo cameraInfo = deviceList.get(position);

//        if (cameraInfo.getStatus()==0)
//            Glide.with(context).load(cameraInfo.getDeviceIconGray()).into(holder.imgDeviceIcon);
//        else
        Glide.with(context).load(cameraInfo.getDeviceIcon()).into(holder.imgDeviceIcon);
        holder.tvDeviceName.setText(cameraInfo.getDeviceName()+ " - "+cameraInfo.getDeviceID());

        MeariDeviceController deviceController = new MeariDeviceController();
        deviceController.setCameraInfo(cameraInfo);
        MeariUser.getInstance().setCameraInfo(cameraInfo);
        MeariUser.getInstance().setController(deviceController);
        MeariUser.getInstance().getDeviceParams(new IGetDeviceParamsCallback() {
            @Override
            public void onSuccess(DeviceParams deviceParams) {
                int pir = deviceParams.getPirDetEnable();
                int p = deviceParams.getBatteryPercent();
                // int p2 = deviceParams.getBatteryRemaining();
                // int a1 = deviceParams.getAllAlarmsEnable();
                // String s = deviceParams.getSoundLightAlarmPlanList();
                // int s1 = deviceParams.getSoundLightType();
                int s2 = deviceParams.getSoundLightEnable();
                Log.d("DeviceListAdapter"," pir:"+pir+" - "+s2);

                holder.itemView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (pir == 0) {
                            holder.imgDetectionStatus.setImageResource(R.mipmap.camera_pause);
                        } else {
                            holder.imgDetectionStatus.setImageResource(R.mipmap.camera_play);
                        }

                        if (s2 == 0) {
                            holder.imgAlarmStatus.setImageResource(R.mipmap.disable_siren);
                        } else {
                            holder.imgAlarmStatus.setImageResource(R.mipmap.enable_siren);
                        }
                    }
                });
            }

            @Override
            public void onFailed(int i, String s) {
                Log.d("DeviceListAdapter","Failed to get cam status: "+i+" "+s);
            }
        });

        holder.deviceView.setOnClickListener(v -> {
            Intent intent = new Intent(context, DeviceMonitorActivity.class);
            Bundle bundle = new Bundle();
            bundle.putSerializable("cameraInfo", deviceList.get(position));
            intent.putExtras(bundle);
            context.startActivity(intent);
        });

        // Add click listeners for PIR detection toggle
        holder.imgDetectionStatus.setOnClickListener(v -> {
            togglePIRDetection(cameraInfo, holder);
        });

        // Add click listeners for alarm toggle
        holder.imgAlarmStatus.setOnClickListener(v -> {
            toggleAlarmStatus(cameraInfo, holder);
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    private void togglePIRDetection(CameraInfo cameraInfo, DeviceHolder holder) {
        // Show loading state
        holder.imgDetectionStatus.setAlpha(0.5f);
        
        CamManager camManager = CamManager.get(context);
        
        // Get current state to determine toggle direction
        MeariDeviceController deviceController = new MeariDeviceController();
        deviceController.setCameraInfo(cameraInfo);
        MeariUser.getInstance().setCameraInfo(cameraInfo);
        MeariUser.getInstance().setController(deviceController);
        
        MeariUser.getInstance().getDeviceParams(new IGetDeviceParamsCallback() {
            @Override
            public void onSuccess(DeviceParams deviceParams) {
                int currentPir = deviceParams.getPirDetEnable();
                boolean shouldEnable = currentPir == 0;
                
                ISetDeviceParamsCallback toggleCallback = new ISetDeviceParamsCallback() {
                    @Override
                    public void onSuccess() {
                        // Update UI on main thread
                        holder.itemView.post(() -> {
                            holder.imgDetectionStatus.setAlpha(1.0f);
                            if (shouldEnable) {
                                holder.imgDetectionStatus.setImageResource(R.mipmap.camera_play);
                                Toast.makeText(context, "PIR enabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                            } else {
                                holder.imgDetectionStatus.setImageResource(R.mipmap.camera_pause);
                                Toast.makeText(context, "PIR disabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailed(int code, String error) {
                        // Restore UI on main thread
                        holder.itemView.post(() -> {
                            holder.imgDetectionStatus.setAlpha(1.0f);
                            Toast.makeText(context, "Failed to toggle PIR: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                };
                
                // Call appropriate method
                if (shouldEnable) {
                    camManager.enableSingleCameraPIR(context, cameraInfo.getDeviceID(), toggleCallback);
                } else {
                    camManager.disableSingleCameraPIR(context, cameraInfo.getDeviceID(), toggleCallback);
                }
            }

            @Override
            public void onFailed(int i, String s) {
                // Restore UI on main thread
                holder.itemView.post(() -> {
                    holder.imgDetectionStatus.setAlpha(1.0f);
                    Toast.makeText(context, "Failed to get device status", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void toggleAlarmStatus(CameraInfo cameraInfo, DeviceHolder holder) {
        // Show loading state
        holder.imgAlarmStatus.setAlpha(0.5f);
        
        CamManager camManager = CamManager.get(context);
        
        // Get current state to determine toggle direction
        MeariDeviceController deviceController = new MeariDeviceController();
        deviceController.setCameraInfo(cameraInfo);
        MeariUser.getInstance().setCameraInfo(cameraInfo);
        MeariUser.getInstance().setController(deviceController);
        
        MeariUser.getInstance().getDeviceParams(new IGetDeviceParamsCallback() {
            @Override
            public void onSuccess(DeviceParams deviceParams) {
                int currentAlarm = deviceParams.getSoundLightEnable();
                boolean shouldEnable = currentAlarm == 0;
                
                ISetDeviceParamsCallback toggleCallback = new ISetDeviceParamsCallback() {
                    @Override
                    public void onSuccess() {
                        // Update UI on main thread
                        holder.itemView.post(() -> {
                            holder.imgAlarmStatus.setAlpha(1.0f);
                            if (shouldEnable) {
                                holder.imgAlarmStatus.setImageResource(R.mipmap.enable_siren);
                                Toast.makeText(context, "Alarm enabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                            } else {
                                holder.imgAlarmStatus.setImageResource(R.mipmap.disable_siren);
                                Toast.makeText(context, "Alarm disabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailed(int code, String error) {
                        // Restore UI on main thread
                        holder.itemView.post(() -> {
                            holder.imgAlarmStatus.setAlpha(1.0f);
                            Toast.makeText(context, "Failed to toggle alarm: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                };
                
                // Call appropriate method
                if (shouldEnable) {
                    camManager.enableSingleCameraAlarm(context, cameraInfo.getDeviceID(), toggleCallback);
                } else {
                    camManager.disableSingleCameraAlarm(context, cameraInfo.getDeviceID(), toggleCallback);
                }
            }

            @Override
            public void onFailed(int i, String s) {
                // Restore UI on main thread
                holder.itemView.post(() -> {
                    holder.imgAlarmStatus.setAlpha(1.0f);
                    Toast.makeText(context, "Failed to get device status", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    class DeviceHolder extends RecyclerView.ViewHolder {
        View deviceView;
        ImageView imgDeviceIcon;
        TextView tvDeviceName;
        ImageView imgDetectionStatus;
        ImageView imgAlarmStatus;

        public DeviceHolder(@NonNull View itemView) {
            super(itemView);
            deviceView = itemView;
            imgDeviceIcon = itemView.findViewById(R.id.img_device_icon);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            imgDetectionStatus = itemView.findViewById(R.id.img_detection_status);
            imgAlarmStatus = itemView.findViewById(R.id.img_alarm_status);
        }
    }
}
