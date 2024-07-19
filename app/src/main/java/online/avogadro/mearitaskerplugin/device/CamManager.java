package online.avogadro.mearitaskerplugin.device;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.MeariSmartSdk;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.DeviceAlarmMessage;
import com.meari.sdk.bean.MeariDevice;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.IDevListCallback;
import com.meari.sdk.callback.IDeviceAlarmMessagesCallback;
import com.meari.sdk.callback.ILoginCallback;
import com.meari.sdk.callback.ISetDeviceParamsCallback;
import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;
import online.avogadro.mearitaskerplugin.app.Util;

import com.ppstrong.utils.MeariMediaUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CamManager {

    interface IDoSomething {
        public void doSomething(ISetDeviceParamsCallback then);
    }

    List<CameraInfo> deviceList = new ArrayList<CameraInfo>();
    Context context;

    public CamManager(Context context) {
        this.context = context;
    }

    public void disableAllCameras() {
        // getDataAndChangeState(false);

        int enableFlag = 0;
        loginAndDoSomethingOnAllCameras(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setPirDetectionEnable(enableFlag ,then);
            }
        });
    }

    public void enableAllCameras() {
        // getDataAndChangeState(true);
        int enableFlag = 1;
        loginAndDoSomethingOnAllCameras(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setPirDetectionEnable(enableFlag ,then);
            }
        });
    }

    public void enableAllCameraAlarms() {
        loginAndDoSomethingOnAllCameras(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                // enableCameraSirenAlarm(true, then);
                MeariUser.getInstance().setFloodCameraVoiceLightAlarmEnable(1, then); // enable alarm
            }
        });
    }

    public void disableAllCameraAlarms() {
        loginAndDoSomethingOnAllCameras(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                // enableCameraSirenAlarm(true, then);
                MeariUser.getInstance().setFloodCameraVoiceLightAlarmEnable(0, then); // disable alarm
            }
        });
    }

    private void loginWithStoredCredentials(ILoginCallback then) {
        if (MeariUser.getInstance().isLogin()) { // avoid double login
            then.onSuccess(MeariUser.getInstance().getUserInfo());
            return;
        }

        String username = SharedPreferencesHelper.get(context,"username");
        String password = SharedPreferencesHelper.get(context,"password");

        if ("".equals(username) || "".equals(password)) {
            Toast.makeText(context,"Failed to login: NO credentials!" , Toast.LENGTH_LONG).show();
            return; // no stored credentials, go on with standard login
        }

        MeariSmartSdk.partnerId= MeariApplication.partnerIdS;
        MeariUser.getInstance().loginWithAccount("IT", "39", username, password, new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                then.onSuccess(userInfo);
            }

            //     public void loginWithThird(String account, String userToken, String userName, String userIcon, String loginType, String countryCode, String phoneCode, ILoginCallback callback) {

            @Override
            public void onError(int i, String s) {
                Toast.makeText(context,"No Camera Action: failed to login" , Toast.LENGTH_LONG).show();
                SharedPreferencesHelper.save(context, "username", "" );
                SharedPreferencesHelper.save(context, "password", "" );
                then.onError(i, s);
            }
        } );
    }

    private void loginAndDoSomethingOnAllCameras(IDoSomething whatToDo) {
        loginWithStoredCredentials(new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                MeariUser.getInstance().getDeviceList(new IDevListCallback() {
                    @Override
                    public void onSuccess(MeariDevice meariDevice) {
                        Log.d("tag", "--->i: ssss");
                        initList(meariDevice);
                        // doSomethingOnCamera(0, whatToDo);
                        doSomethingOnAllCameras(whatToDo);
                    }

                    @Override
                    public void onError(int i, String s) {
                        Log.w("tag", "--->i: " + i + "; s: " + s);
                        Toast.makeText(context, "Failed to enumerate cameras", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(int i, String s) {
                Log.w("tag", "Error --->i: " + i + "; s: " + s);
                Toast.makeText(context, "Failed to apply action: "+s, Toast.LENGTH_LONG).show();
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
    }

    /**
     * Apply an action to the all the cameras in parallel
     * @param whatToDo action to apply to camera
     */
    private void doSomethingOnAllCameras(IDoSomething whatToDo) {

        for (CameraInfo cameraInfo: deviceList) {
            MeariDeviceController deviceController = new MeariDeviceController();
            deviceController.setCameraInfo(cameraInfo);

            // Set the device to be controlled
            MeariUser.getInstance().setCameraInfo(cameraInfo);
            MeariUser.getInstance().setController(deviceController);

            whatToDo.doSomething( new ISetDeviceParamsCallback() {
                @Override
                public void onSuccess() {
                    Log.d("tag", "--->camera "+cameraInfo.getDeviceName()+" camera configuration success");
                }

                @Override
                public void onFailed(int i, String s) {
                    Log.w("tag", "--->camera "+cameraInfo.getDeviceName()+" camera configuration failed "+s);
                    Toast.makeText(context, "Failed on "+cameraInfo.getDeviceName()+" : "+s, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Return the list of events coming from the camera, used to extract the event's image or other info
     * Will return the download image's local path in CameraInfo.firmID
     *
     * @param cameraID
     * @param res
     */
    public void getLastAlertImage(long cameraID, IDeviceAlarmMessagesCallback res) {
        getLastAlertImage(new Date(), cameraID, res);
    }
    /**
     * Return the list of events coming from the camera, used to extract the event's image or other info
     * Will return the download image's local path in CameraInfo.firmID
     *
     * @param cameraID
     * @param res
     */
    public void getLastAlertImage(Date date, long cameraID, IDeviceAlarmMessagesCallback res) {
        if (Util.getDaysBetween(new Date(),date)>10) { // don't look more then 10 days back
            res.onError(-1, "list of events was empty, no event image available");
            return;
        }

        SimpleDateFormat DateFor = new SimpleDateFormat("yyyyMMdd");
        String dateNow= DateFor.format(date);
        MeariUser.getInstance().getAlertMsgWithVideo(cameraID, dateNow, "1", 1, 0, null, new IDeviceAlarmMessagesCallback() {
            @Override
            public void onSuccess(List<DeviceAlarmMessage> list, CameraInfo cameraInfo) {
                if (list.isEmpty()) {
                    getLastAlertImage(Util.sendDateBackOneDay(date), cameraID, res);
                    // res.onError(-1, "list of events was empty, no event image available");
                } else {
                    downloadAlertImagePreviews(list, cameraInfo, new IDeviceAlarmMessagesCallback() {
                        @Override
                        public void onSuccess(List<DeviceAlarmMessage> list, CameraInfo ci) {
                            // very lazy developer.... re-using an existing class and lister :-P
                            cameraInfo.setFirmID(ci.getFirmID());
                            res.onSuccess(new ArrayList<DeviceAlarmMessage>(),cameraInfo);
                        }

                        @Override
                        public void onError(int i, String s) {
                            res.onError(i,s);
                        }
                    });
                }
            }

            @Override
            public void onError(int i, String s) {
                res.onError(i,s);
            }
        });
    }

    public static byte[] getImageBytes(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream stream = url.openStream()) {
                byte[] buffer = new byte[4096];
                while (true) {
                    int bytesRead = stream.read(buffer);
                    if (bytesRead < 0) {
                        break;
                    }
                    output.write(buffer, 0, bytesRead);
                }
            }
            return output.toByteArray();
        } catch (IOException e) {
            Log.e("CamManager", "Failed to download file: "+imageUrl);
            return null;
        }
    }

    void downloadAlertImagePreviews(List<DeviceAlarmMessage> list, CameraInfo cameraInfo, IDeviceAlarmMessagesCallback res) {
        Log.d("Devicelist", "AA " + list);
        // downloadAlertImagePreviews(list.get(0).getImageUrl());
        DeviceAlarmMessage latest = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            if (Long.parseLong(list.get(i).getEventTime()) > Long.parseLong(latest.getEventTime())) {
                latest = list.get(i);
            }
        }
        new DownloadImageInBackground(res).execute(latest.getImageUrl(), cameraInfo.getSnNum());
    }

    static Uri downloadAlertImagePreviews(String imageUrl, String cameraSN) {
        try {
            // Glide.with(DeviceListActivity.this).load(cameraInfo.getDeviceIcon()).into(imgAdd);
            // Glide.with(DeviceListActivity.this).load(list.get(0).getImageUrl()).into(imgAdd);
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.TITLE, "PROVA.jpg");
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "Image"+System.currentTimeMillis()/1000+".jpg");
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.DATE_ADDED, Long.valueOf(System.currentTimeMillis() / 1000));

            File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            contentValues.put(MediaStore.Images.Media.DATA, externalStoragePublicDirectory.getAbsolutePath());
            Uri uri3 = MeariApplication.getInstance().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            byte[] raw = CamManager.getImageBytes(imageUrl);
            // Bitmap decodeFile = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            String filename = imageUrl;
            String sn = cameraSN;
            MeariMediaUtil.decodePic(filename,sn,raw);
            Bitmap decodeFile = BitmapFactory.decodeByteArray(raw,0,raw.length);
            OutputStream openOutputStream = MeariApplication.getInstance().getContentResolver().openOutputStream(uri3);
            decodeFile.compress(Bitmap.CompressFormat.JPEG, 90, openOutputStream);
            decodeFile.recycle();
            // Bitmap decodeFile = BitmapFactory.decodeStream(new URL(imageUrl).openStream());
            // decodeFile.compress(Bitmap.CompressFormat.JPEG, 90, openOutputStream);
            // decodeFile.recycle();
            return uri3;
        } catch (IOException fnfe) {
            Log.d("aaa","bbb");
            return null;
        }
    }

    class DownloadImageInBackground extends AsyncTask<String, Void, String> {

        private Exception exception;
        IDeviceAlarmMessagesCallback res;

        DownloadImageInBackground(IDeviceAlarmMessagesCallback res) {
            super();
            this.res=res;
        }

        protected String doInBackground(String... params) {
            try {
                Uri uri3 = downloadAlertImagePreviews(params[0], params[1]);
                // Glide.with(MeariApplication.getInstance()).load(uri3).into(imgAdd);
                return uri3.toString();
            } catch (Exception e) {
                this.exception = e;
            }
            return null;
        }

        protected void onPostExecute(String localPath) {
            CameraInfo ci = new CameraInfo();
            ci.setFirmID(localPath);
            res.onSuccess(null,ci);
        }
    }

}
