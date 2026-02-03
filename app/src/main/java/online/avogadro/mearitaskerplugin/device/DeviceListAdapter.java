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

import androidx.appcompat.app.AlertDialog;
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
    private boolean showCameraId = true;

    public DeviceListAdapter(Context context, List<CameraInfo> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
    }

    public void setShowCameraId(boolean showCameraId) {
        this.showCameraId = showCameraId;
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
        Log.d("DeviceListAdapter", "onBindViewHolder called for: " + cameraInfo.getDeviceName());

//        if (cameraInfo.getStatus()==0)
//            Glide.with(context).load(cameraInfo.getDeviceIconGray()).into(holder.imgDeviceIcon);
//        else
        Glide.with(context).load(cameraInfo.getDeviceIcon()).into(holder.imgDeviceIcon);
        if (showCameraId) {
            holder.tvDeviceName.setText(cameraInfo.getDeviceName() + " - " + cameraInfo.getDeviceID());
        } else {
            holder.tvDeviceName.setText(cameraInfo.getDeviceName());
        }

        MeariDeviceController deviceController = new MeariDeviceController();
        deviceController.setCameraInfo(cameraInfo);
        MeariUser.getInstance().setCameraInfo(cameraInfo);
        MeariUser.getInstance().setController(deviceController);
        MeariUser.getInstance().getDeviceParams(cameraInfo, new IGetDeviceParamsCallback() {
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
                            holder.imgDetectionStatus.setTag(false); // Cache PIR disabled state
                        } else {
                            holder.imgDetectionStatus.setImageResource(R.mipmap.camera_play);
                            holder.imgDetectionStatus.setTag(true); // Cache PIR enabled state
                        }

                        if (s2 == 0) {
                            holder.imgAlarmStatus.setImageResource(R.mipmap.disable_siren);
                            holder.imgAlarmStatus.setTag(false); // Cache alarm disabled state
                        } else {
                            holder.imgAlarmStatus.setImageResource(R.mipmap.enable_siren);
                            holder.imgAlarmStatus.setTag(true); // Cache alarm enabled state
                        }
                    }
                });
            }

            @Override
            public void onFailed(int i, String s) {
                Log.d("DeviceListAdapter","Failed to get cam status: "+ cameraInfo.getDeviceName()+ " - code:"+i+" error:"+s);
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

        // Add click listener for firing siren alarm on single camera
        holder.imgFireAlarm.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Fire siren?")
                    .setMessage("Fire the siren on " + cameraInfo.getDeviceName() + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        holder.imgFireAlarm.setAlpha(0.5f);
                        Toast.makeText(context, "Firing siren on " + cameraInfo.getDeviceName() + "...", Toast.LENGTH_SHORT).show();
                        CamManager.get(context).fireSirenAlarm(context, cameraInfo.getDeviceID(), new ISetDeviceParamsCallback() {
                            @Override
                            public void onSuccess() {
                                holder.itemView.post(() -> {
                                    holder.imgFireAlarm.setAlpha(1.0f);
                                    Toast.makeText(context, "Siren fired on " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onFailed(int code, String error) {
                                holder.itemView.post(() -> {
                                    holder.imgFireAlarm.setAlpha(1.0f);
                                    Toast.makeText(context, "Failed to fire siren: " + error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    private boolean isDetectionEnabled(ImageView imageView) {
        // Check current icon state to determine if PIR detection is enabled
        if (imageView.getTag() != null) {
            return (Boolean) imageView.getTag();
        }
        // Fallback: check drawable resource (less reliable but works)
        return imageView.getDrawable() != null;
    }

    private boolean isAlarmEnabled(ImageView imageView) {
        // Check current icon state to determine if alarm is enabled
        if (imageView.getTag() != null) {
            return (Boolean) imageView.getTag();
        }
        // Fallback: check drawable resource (less reliable but works)
        return imageView.getDrawable() != null;
    }

    private void togglePIRDetection(CameraInfo cameraInfo, DeviceHolder holder) {
        // Show loading state
        holder.imgDetectionStatus.setAlpha(0.5f);
        
        // Get current state from icon cache (no API call needed!)
        boolean currentlyEnabled = isDetectionEnabled(holder.imgDetectionStatus);
        boolean shouldEnable = !currentlyEnabled;
        
        CamManager camManager = CamManager.get(context);
        
        ISetDeviceParamsCallback toggleCallback = new ISetDeviceParamsCallback() {
            @Override
            public void onSuccess() {
                // Update UI on main thread
                holder.itemView.post(() -> {
                    holder.imgDetectionStatus.setAlpha(1.0f);
                    if (shouldEnable) {
                        holder.imgDetectionStatus.setImageResource(R.mipmap.camera_play);
                        holder.imgDetectionStatus.setTag(true); // Cache new state
                        Toast.makeText(context, "PIR enabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                    } else {
                        holder.imgDetectionStatus.setImageResource(R.mipmap.camera_pause);
                        holder.imgDetectionStatus.setTag(false); // Cache new state
                        Toast.makeText(context, "PIR disabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailed(int code, String error) {
                // Restore UI on main thread - keep previous state
                holder.itemView.post(() -> {
                    holder.imgDetectionStatus.setAlpha(1.0f);
                    Toast.makeText(context, "Failed to toggle PIR: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        };
        
        // Call appropriate method based on current state
        if (shouldEnable) {
            camManager.enableSingleCameraPIR(context, cameraInfo.getDeviceID(), toggleCallback);
        } else {
            camManager.disableSingleCameraPIR(context, cameraInfo.getDeviceID(), toggleCallback);
        }
    }

    private void toggleAlarmStatus(CameraInfo cameraInfo, DeviceHolder holder) {
        // Show loading state
        holder.imgAlarmStatus.setAlpha(0.5f);
        
        // Get current state from icon cache (no API call needed!)
        boolean currentlyEnabled = isAlarmEnabled(holder.imgAlarmStatus);
        boolean shouldEnable = !currentlyEnabled;
        
        CamManager camManager = CamManager.get(context);
        
        ISetDeviceParamsCallback toggleCallback = new ISetDeviceParamsCallback() {
            @Override
            public void onSuccess() {
                // Update UI on main thread
                holder.itemView.post(() -> {
                    holder.imgAlarmStatus.setAlpha(1.0f);
                    if (shouldEnable) {
                        holder.imgAlarmStatus.setImageResource(R.mipmap.enable_siren);
                        holder.imgAlarmStatus.setTag(true); // Cache new state
                        Toast.makeText(context, "Alarm enabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                    } else {
                        holder.imgAlarmStatus.setImageResource(R.mipmap.disable_siren);
                        holder.imgAlarmStatus.setTag(false); // Cache new state
                        Toast.makeText(context, "Alarm disabled for " + cameraInfo.getDeviceName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailed(int code, String error) {
                // Restore UI on main thread - keep previous state
                holder.itemView.post(() -> {
                    holder.imgAlarmStatus.setAlpha(1.0f);
                    Toast.makeText(context, "Failed to toggle alarm: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        };
        
        // Call appropriate method based on current state
        if (shouldEnable) {
            camManager.enableSingleCameraAlarm(context, cameraInfo.getDeviceID(), toggleCallback);
        } else {
            camManager.disableSingleCameraAlarm(context, cameraInfo.getDeviceID(), toggleCallback);
        }
    }

    class DeviceHolder extends RecyclerView.ViewHolder {
        View deviceView;
        ImageView imgDeviceIcon;
        TextView tvDeviceName;
        ImageView imgDetectionStatus;
        ImageView imgAlarmStatus;
        ImageView imgFireAlarm;

        public DeviceHolder(@NonNull View itemView) {
            super(itemView);
            deviceView = itemView;
            imgDeviceIcon = itemView.findViewById(R.id.img_device_icon);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            imgDetectionStatus = itemView.findViewById(R.id.img_detection_status);
            imgAlarmStatus = itemView.findViewById(R.id.img_alarm_status);
            imgFireAlarm = itemView.findViewById(R.id.img_fire_alarm);
        }
    }
}
