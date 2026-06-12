package online.avogadro.mearitaskerplugin.device;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import android.util.Log;
import android.widget.Toast;

import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.MeariIotManager;
import com.meari.sdk.MeariSmartSdk;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.DeviceAlarmMessage;
import com.meari.sdk.bean.MeariDevice;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.IDevListCallback;
import com.meari.sdk.callback.IDeviceAlarmMessagesCallback;
import com.meari.sdk.callback.ILoginCallback;
import com.meari.sdk.callback.IResultCallback;
import com.meari.sdk.callback.ISetDeviceParamsCallback;
import online.avogadro.mearitaskerplugin.app.MeariApplication;
import online.avogadro.mearitaskerplugin.app.SharedPreferencesHelper;
import online.avogadro.mearitaskerplugin.app.Util;
import online.avogadro.mearitaskerplugin.tasker.CameraResolver;

import com.meari.sdk.listener.MeariDeviceListener;
import com.meari.sdk.utils.Logger;
import com.meari.sdk.utils.MeariExecutors;
import com.meari.sdk.utils.SdkUtils;
import com.ppstrong.ppsplayer.CameraPlayer;
import com.ppstrong.ppsplayer.CameraPlayerListener;
import com.ppstrong.ppsplayer.PPSGLSurfaceView;
import com.ppstrong.ppsplayer.PPSMediaCodec;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.meari.sdk.callback.IGetDeviceStatusCallback;

public class CamManager {

    public static final int DEVICE_LIST_RELOAD_TIMEOUT = 120000;

    ISetDeviceParamsCallback DO_NOTHING = new ISetDeviceParamsCallback() {
        @Override
        public void onSuccess() {
            // nothing
        }

        @Override
        public void onFailed(int i, String s) {
            // nothing
        }
    };

    MeariDeviceListener NOOP_DEVICE_LISTENER = new MeariDeviceListener() {
        @Override public void onSuccess(String msg) {}
        @Override public void onFailed(String errorMsg) {}
    };

    class MyMeariDeviceController extends MeariDeviceController {
        private CameraPlayer cameraPlayer2 = null;

        public MyMeariDeviceController() {
            super();
            if (this.cameraPlayer2 == null) {
                this.cameraPlayer2 = new CameraPlayer();
            }
        }
        public void startConnect(String pwd, final MeariDeviceListener deviceListener) {
            String connectString = SdkUtils.getConnectString(this.getCameraInfo(), pwd);
            Logger.i("MyMeariDeviceController", "--->startConnect--object: " + this.toString() + "; string: " + SdkUtils.getConnectString(this.getCameraInfo()));
            this.cameraPlayer2.connectIPC2(connectString, new CameraPlayerListener() {
                public void PPSuccessHandler(final String successMsg) {
                    Logger.i("MyMeariDeviceController", "--->startConnect--success--object: " + MyMeariDeviceController.this.toString() + "--" + successMsg);
                    //MeariExecutors.runOnMainThread(new Runnable() {
                    //    public void run() {
                            deviceListener.onSuccess(successMsg);
                    //    }
                    //});
                }

                public void PPFailureError(String errorCode) {
                    Logger.i("MyMeariDeviceController", "--->startConnect--failed--object: " + MyMeariDeviceController.this + "--" + errorCode);
                    final String s = errorCode;
                    // MeariExecutors.runOnMainThread(new Runnable() {
                    //    public void run() {
                            deviceListener.onFailed(s);
                    //    }
                    //});
                }
            });
        }

    }

    public static interface IDoSomething {
        public void doSomething(ISetDeviceParamsCallback then);
        public String description();
    }

    public interface ICameraOperationCallback {
        void onCameraSuccess(CameraInfo cameraInfo);
        void onCameraFailed(CameraInfo cameraInfo, int code, String error);
    }

    List<CameraInfo> deviceList = new ArrayList<CameraInfo>();

    long deviceListLastReload = 0L;

    Context context;

    public CamManager(Context context) {
        this.context = context;
    }

    /**
     * Toast that is safe to call from any thread: SDK callbacks may arrive on
     * native/non-Looper threads, where Toast.makeText() would throw.
     */
    private void toast(final String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show());
    }

    private static CamManager INSTANCE = null;
    public static CamManager get(Context context) {
        if (INSTANCE==null) {
            INSTANCE=new CamManager(context);
        }
        return INSTANCE;
    }

    public List<CameraInfo> getDeviceList() {
        return deviceList;
    }

    public void disableAllCameras(List<CameraInfo> cameras) {
        disableAllCameras(cameras, null);
    }

    public void disableAllCameras(List<CameraInfo> cameras, ICameraOperationCallback perCameraCallback) {
        doSomethingOnCameras(cameras, pirAction(0), perCameraCallback);
    }

    public void enableAllCameras(List<CameraInfo> cameras) {
        enableAllCameras(cameras, null);
    }

    public void enableAllCameras(List<CameraInfo> cameras, ICameraOperationCallback perCameraCallback) {
        doSomethingOnCameras(cameras, pirAction(1), perCameraCallback);
    }

    private IDoSomething pirAction(final int enableFlag) {
        return new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setPirDetectionEnable(enableFlag, then);
            }
            @Override
            public String description() {
                return enableFlag == 1 ? "Enable movement detection" : "Disable movement detection";
            }
        };
    }

    private IDoSomething sirenAlarmAction(final int enableFlag) {
        return new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setFloodCameraVoiceLightAlarmEnable(enableFlag, then);
            }
            @Override
            public String description() {
                return enableFlag == 1 ? "Enable alarm on detection" : "Disable alarm on detection";
            }
        };
    }

    public void enableAllCameraAlarms(List<CameraInfo> cameras) {
        enableAllCameraAlarms(cameras, null);
    }

    public void enableAllCameraAlarms(List<CameraInfo> cameras, ICameraOperationCallback perCameraCallback) {
        doSomethingOnCameras(cameras, sirenAlarmAction(1), perCameraCallback);
    }

    public void fireAllSirenAlarms(List<CameraInfo> cameras) {
        fireAllSirenAlarms(cameras, null);
    }

    public void fireAllSirenAlarms(List<CameraInfo> cameras, ICameraOperationCallback perCameraCallback) {
        doSomethingOnCameras(cameras, new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setFlightSirenEnable(1, then);
            }
            @Override
            public String description() { return "Fire siren alarm"; }
        }, perCameraCallback);
    }

    public void disableAllCameraAlarms(List<CameraInfo> cameras) {
        disableAllCameraAlarms(cameras, null);
    }

    public void disableAllCameraAlarms(List<CameraInfo> cameras, ICameraOperationCallback perCameraCallback) {
        doSomethingOnCameras(cameras, sirenAlarmAction(0), perCameraCallback);
    }

    public void enableAllCameraAlarms() {
        enableAllCameraAlarms((ISetDeviceParamsCallback) null);
    }

    public void enableAllCameraAlarms(ISetDeviceParamsCallback event) {
        setAllCameraAlarms(1, event);
    }

    public void disableAllCameraAlarms() {
        disableAllCameraAlarms((ISetDeviceParamsCallback) null);
    }

    public void disableAllCameraAlarms(ISetDeviceParamsCallback event) {
        setAllCameraAlarms(0, event);
    }

    private void setAllCameraAlarms(final int enableFlag, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                doSomethingOnCamerasAndReport(new ArrayList<>(deviceList), sirenAlarmAction(enableFlag), event);
            }
            @Override
            public String description() {
                return enableFlag == 1 ? "Enable alarm on detection" : "Disable alarm on detection";
            }
        }, event);
    }

    private void loginWithStoredCredentials(ILoginCallback then) {
        if (MeariUser.getInstance().isLogin()) { // avoid double login
            then.onSuccess(MeariUser.getInstance().getUserInfo());
            return;
        }

        String username = SharedPreferencesHelper.get(context,"username");
        String password = SharedPreferencesHelper.get(context,"password");

        if ("".equals(username) || "".equals(password)) {
            toast("Failed to login: NO credentials!" );
            then.onError(-100, "No stored credentials");
            return;
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
                toast("No Camera Action: failed to login" );
                SharedPreferencesHelper.save(context, "username", "" );
                SharedPreferencesHelper.save(context, "password", "" );
                then.onError(i, s);
            }
        } );
    }

    private void updateDeviceListAndDoSomething(IDoSomething whatToDo) {
        updateDeviceListAndDoSomething(whatToDo, null);
    }

    private void updateDeviceListAndDoSomething(IDoSomething whatToDo, ISetDeviceParamsCallback errorEvent) {
        if (!deviceList.isEmpty() && System.currentTimeMillis()<deviceListLastReload+ DEVICE_LIST_RELOAD_TIMEOUT){
            Log.d("CamManager", "using cam list from cache");
            whatToDo.doSomething(DO_NOTHING);
            return;
        }

        MeariUser.getInstance().getDeviceList(new IDevListCallback() {
            @Override
            public void onSuccess(MeariDevice meariDevice) {
                Log.d("CamManager", "listDevices ok");
                initList(meariDevice);

                whatToDo.doSomething(DO_NOTHING);
            }

            @Override
            public void onError(int i, String s) {
                Log.w("CamManager", "--->i: " + i + "; s: " + s);
                toast("Failed to enumerate cameras");
                if (errorEvent != null) errorEvent.onFailed(i, "Failed to enumerate cameras: " + s);
            }
        });
    }

    public void loginAndInitList(IDoSomething whatToDo) {
        loginAndInitList(whatToDo, null);
    }

    /**
     * Like {@link #loginAndInitList(IDoSomething)}, but login/device-list failures are
     * also reported to errorEvent.onFailed() so callers waiting for a result never hang.
     */
    public void loginAndInitList(IDoSomething whatToDo, ISetDeviceParamsCallback errorEvent) {
        loginWithStoredCredentials(new ILoginCallback() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                updateDeviceListAndDoSomething(whatToDo, errorEvent);
            }

            @Override
            public void onError(int i, String s) {
                Log.w("CamManager", "Error --->i: " + i + "; s: " + s);
                toast("Failed to apply action: "+s);
                if (errorEvent != null) errorEvent.onFailed(i, "Login failed: " + s);
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

        deviceListLastReload = System.currentTimeMillis();
    }

    /**
     * Wakes a battery camera and polls until it is online, then calls onSuccess().
     * Polls every 3 s for up to 10 attempts (~30 s total).
     * Calls onFailed(-15, msg) if the camera does not come online within the timeout.
     */
    private void wakeCamera(String snNum, ISetDeviceParamsCallback callback) {
        MeariIotManager.getInstance().init();
        MeariIotManager.getInstance().wakeDevice(snNum);
        new Thread(() -> {
            final int maxAttempts = 10;
            final int intervalMs  = 3_000;
            for (int i = 0; i < maxAttempts; i++) {
                try { Thread.sleep(intervalMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

                CountDownLatch latch  = new CountDownLatch(1);
                boolean[] online      = {false};
                MeariIotManager.getInstance().getDeviceStatusGet(snNum,
                    new IGetDeviceStatusCallback() {
                        @Override public void onSuccess(boolean isOnline) {
                            online[0] = isOnline;
                            latch.countDown();
                        }
                        @Override public void onFailed(int code, String msg) {
                            latch.countDown(); // treat HTTP error as "not yet online"
                        }
                    });
                try { latch.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

                Log.d("CamManager", "wakeCamera poll " + (i + 1) + "/" + maxAttempts
                        + " sn=" + snNum + " online=" + online[0]);
                if (online[0]) { callback.onSuccess(); return; }
            }
            callback.onFailed(-15, "Camera did not come online within "
                    + (maxAttempts * intervalMs / 1000) + "s");
        }).start();
    }

    public void takeAPicture(Context context, String camera, MeariDeviceListener event) {

        loginAndInitList(new IDoSomething() {

            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                CameraInfo cameraInfo = null;
                for (CameraInfo ci : deviceList) {
                    if (camera.equals(ci.getDeviceID())) {
                        cameraInfo = ci;
                        break;
                    }
                }
                if (cameraInfo == null) {
                    event.onFailed("CameraID not found: " + camera);
                    return;
                }
                final CameraInfo finalCameraInfo = cameraInfo;

                MeariDeviceController deviceController = new MeariDeviceController();
                deviceController.setCameraInfo(finalCameraInfo);
                MeariUser.getInstance().setCameraInfo(finalCameraInfo);
                MeariUser.getInstance().setController(deviceController);

                // Force SOFT (ffmpeg) decode mode: in SOFT mode, snapshot() calls the native
                // snapShot() which reads from the ffmpeg frame buffer - no OpenGL/EGL needed,
                // so the dummy surface below does not need to be attached to any window.
                PPSMediaCodec.setGlobalEnable(false);

                // Dummy off-screen surface. Its renderer ByteBuffers (y/u/v) are allocated by
                // the constructor. In SOFT mode, native ffmpeg writes YUV directly to them via
                // setRenderBuffer() - no EGL context is required for this.
                PPSGLSurfaceView dummySurface = new PPSGLSurfaceView(context, 320, 240);
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .getAbsolutePath() + "/CloudEdge4TaskerSnapshot" + System.currentTimeMillis() + ".jpg";
                int videoId = 0; // main stream (HD); was: CommonUtils.getDefaultStreamId() which returned sub-stream (1) → 640×360

                // Battery cameras sleep between uses. Wake the device and poll until it is
                // online before attempting P2P connection (adaptive wait, max ~30 s).
                wakeCamera(finalCameraInfo.getSnNum(), new ISetDeviceParamsCallback() {
                    @Override
                    public void onSuccess() {
                        deviceController.startConnect(new MeariDeviceListener() {
                            @Override
                            public void onSuccess(String connectMsg) {
                                // startPreview triggers native ffmpeg decode into dummySurface.renderer.y/u/v.
                                // onSuccess fires via the native startPlaySuccessCallback() JNI call
                                // when the first frame is decoded - no sleep required.
                                deviceController.startPreview(dummySurface, videoId, new MeariDeviceListener() {
                                    @Override
                                    public void onSuccess(String firstFrameMsg) {
                                        // First frame is in the native ffmpeg buffer. snapshot() in SOFT
                                        // mode calls native snapShot() which reads that buffer and writes JPEG.
                                        deviceController.snapshot(path, new MeariDeviceListener() {
                                            @Override
                                            public void onSuccess(String snapshotMsg) {
                                                deviceController.stopPreview(NOOP_DEVICE_LISTENER);
                                                deviceController.stopConnect(NOOP_DEVICE_LISTENER);
                                                PPSMediaCodec.setGlobalEnable(true);
                                                MediaScannerConnection.scanFile(context, new String[]{path}, new String[]{"image/jpeg"}, null);
                                                event.onSuccess(path);
                                            }

                                            @Override
                                            public void onFailed(String errorMsg) {
                                                deviceController.stopPreview(NOOP_DEVICE_LISTENER);
                                                deviceController.stopConnect(NOOP_DEVICE_LISTENER);
                                                PPSMediaCodec.setGlobalEnable(true);
                                                event.onFailed(errorMsg);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onFailed(String errorMsg) {
                                        deviceController.stopConnect(NOOP_DEVICE_LISTENER);
                                        PPSMediaCodec.setGlobalEnable(true);
                                        event.onFailed(errorMsg);
                                    }
                                }, null);
                            }

                            @Override
                            public void onFailed(String errorMsg) {
                                PPSMediaCodec.setGlobalEnable(true);
                                event.onFailed(errorMsg);
                            }
                        });
                    }

                    @Override
                    public void onFailed(int code, String msg) {
                        PPSMediaCodec.setGlobalEnable(true);
                        event.onFailed(msg);
                    }
                });
            }

            @Override
            public String description() {
                return "Take a picture";
            }
        });
    }

    public void disableSingleCameraPIR(Context context, String camera, ISetDeviceParamsCallback event) {
        controlSingleCameraPIR(context,camera,0,event);
    }
    public void enableSingleCameraPIR(Context context, String camera, ISetDeviceParamsCallback event) {
        controlSingleCameraPIR(context,camera,1,event);
    }
    private void controlSingleCameraPIR(Context context, String camera, int enableFlag, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {

            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                // extract camera info
                CameraInfo cameraInfo = null;
                for (CameraInfo ci: deviceList) {
                    if (camera.equals(ci.getDeviceID())) {
                        cameraInfo = ci;
                        break;
                    }
                }
                if (cameraInfo==null) {
                    if (event!=null)
                        event.onFailed(-1, "CameraID not found: "+camera);
                    return;
                }
                MeariDeviceController deviceController = new MeariDeviceController();
                deviceController.setCameraInfo(cameraInfo);
                MeariUser.getInstance().setCameraInfo(cameraInfo);
                MeariUser.getInstance().setController(deviceController);
                MeariUser.getInstance().setPirDetectionEnable(enableFlag ,event);
            }
            @Override
            public String description() {
                return "Disable camera PIR";
            }
        });

    }

    public void enableSingleCameraAlarm(Context context, String camera, ISetDeviceParamsCallback event) {
        controlSingleCameraAlarm(context, camera, 1, event);
    }
    
    public void disableSingleCameraAlarm(Context context, String camera, ISetDeviceParamsCallback event) {
        controlSingleCameraAlarm(context, camera, 0, event);
    }
    
    private void controlSingleCameraAlarm(Context context, String camera, int enableFlag, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                CameraInfo cameraInfo = null;
                for (CameraInfo ci: deviceList) {
                    if (camera.equals(ci.getDeviceID())) {
                        cameraInfo = ci;
                        break;
                    }
                }
                if (cameraInfo==null) {
                    if (event!=null)
                        event.onFailed(-1, "CameraID not found: "+camera);
                    return;
                }
                MeariDeviceController deviceController = new MeariDeviceController();
                deviceController.setCameraInfo(cameraInfo);
                MeariUser.getInstance().setCameraInfo(cameraInfo);
                MeariUser.getInstance().setController(deviceController);
                MeariUser.getInstance().setFloodCameraVoiceLightAlarmEnable(enableFlag, event);
            }
            @Override
            public String description() {
                return enableFlag == 1 ? "Enable camera alarm" : "Disable camera alarm";
            }
        });
    }

    public void fireSirenAlarm(Context context, String camera, ISetDeviceParamsCallback event) {

        loginAndInitList(new IDoSomething() {

            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                // extract camera info
                CameraInfo cameraInfo = null;
                for (CameraInfo ci: deviceList) {
                    if (camera.equals(ci.getDeviceID())) {
                        cameraInfo = ci;
                        break;
                    }
                }
                if (cameraInfo==null) {
                    event.onFailed(-1, "CameraID not found: "+camera);
                    return;
                }

                MeariDeviceController deviceController = new MeariDeviceController();
                deviceController.setCameraInfo(cameraInfo);
                MeariUser.getInstance().setCameraInfo(cameraInfo);
                MeariUser.getInstance().setController(deviceController);


                wakeCamera(cameraInfo.getSnNum(), new ISetDeviceParamsCallback() {
                    @Override
                    public void onSuccess() {
                        MeariUser.getInstance().setFlightSirenEnable(1, new ISetDeviceParamsCallback() {
                            @Override
                            public void onSuccess() {
                                event.onSuccess();
                            }

                            @Override
                            public void onFailed(int i, String s) {
                                event.onFailed(i, s);
                            }
                        });
                    }

                    @Override
                    public void onFailed(int i, String s) {
                        event.onFailed(i, s);
                    }
                });
            }
            @Override
            public String description() {
                return "Start camera alarm siren";
            }

        });

    }

    /**
     * Wake a list of cameras, wait once, then execute an action on each.
     */
    private void wakeAndDoSomethingOnCameras(List<CameraInfo> cameras, IDoSomething whatToDo, ISetDeviceParamsCallback event) {
        if (cameras.isEmpty()) {
            if (event != null) event.onFailed(-1, "No cameras matched");
            return;
        }
        MeariIotManager.getInstance().init();
        for (CameraInfo ci : cameras) {
            MeariIotManager.getInstance().wakeDevice(ci.getSnNum());
        }
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            // ignore
        }
        doSomethingOnCamerasAndReport(cameras, whatToDo, event);
    }

    public void fireSirenOnCameras(List<CameraInfo> cameras, ISetDeviceParamsCallback event) {
        wakeAndDoSomethingOnCameras(cameras, new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setFlightSirenEnable(1, then);
            }
            @Override
            public String description() { return "Fire siren alarm"; }
        }, event);
    }

    public void turnOnLightOnCameras(List<CameraInfo> cameras, ISetDeviceParamsCallback event) {
        wakeAndDoSomethingOnCameras(cameras, new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                MeariUser.getInstance().setFlightLightStatus(1, then);
            }
            @Override
            public String description() { return "Turn on camera light"; }
        }, event);
    }

    // --- Selector-based methods: login + resolve + action ---

    public void enableCamerasPIR(String selector, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                List<CameraInfo> matched = CameraResolver.resolve(selector, deviceList);
                if (matched.isEmpty()) {
                    if (event != null) event.onFailed(-1, "No cameras matched selector: " + selector);
                    return;
                }
                doSomethingOnCamerasAndReport(matched, pirAction(1), event);
            }
            @Override
            public String description() { return "Enable PIR detection"; }
        }, event);
    }

    public void disableCamerasPIR(String selector, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                List<CameraInfo> matched = CameraResolver.resolve(selector, deviceList);
                if (matched.isEmpty()) {
                    if (event != null) event.onFailed(-1, "No cameras matched selector: " + selector);
                    return;
                }
                doSomethingOnCamerasAndReport(matched, pirAction(0), event);
            }
            @Override
            public String description() { return "Disable PIR detection"; }
        }, event);
    }

    public void fireSirenOnCameras(String selector, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                List<CameraInfo> matched = CameraResolver.resolve(selector, deviceList);
                if (matched.isEmpty()) {
                    if (event != null) event.onFailed(-1, "No cameras matched selector: " + selector);
                    return;
                }
                fireSirenOnCameras(matched, event);
            }
            @Override
            public String description() { return "Fire siren"; }
        }, event);
    }

    public void turnOnLightOnCameras(String selector, ISetDeviceParamsCallback event) {
        loginAndInitList(new IDoSomething() {
            @Override
            public void doSomething(ISetDeviceParamsCallback then) {
                List<CameraInfo> matched = CameraResolver.resolve(selector, deviceList);
                if (matched.isEmpty()) {
                    if (event != null) event.onFailed(-1, "No cameras matched selector: " + selector);
                    return;
                }
                turnOnLightOnCameras(matched, event);
            }
            @Override
            public String description() { return "Turn on light"; }
        }, event);
    }

    /**
     * Apply an action to a list of cameras in parallel and invoke event exactly once
     * when ALL cameras have answered (or failed): onSuccess() if every camera succeeded,
     * onFailed() with a summary otherwise. Used by Tasker/MacroDroid actions, which must
     * report completion only when the operation has really finished.
     */
    private void doSomethingOnCamerasAndReport(List<CameraInfo> cameras, IDoSomething whatToDo, ISetDeviceParamsCallback event) {
        if (event == null) {
            doSomethingOnCameras(cameras, whatToDo, null);
            return;
        }
        if (cameras.isEmpty()) {
            event.onFailed(-1, "No cameras to operate on");
            return;
        }
        final int total = cameras.size();
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicInteger failed = new AtomicInteger(0);
        final AtomicReference<String> firstError = new AtomicReference<>();
        doSomethingOnCameras(cameras, whatToDo, new ICameraOperationCallback() {
            @Override
            public void onCameraSuccess(CameraInfo cameraInfo) {
                complete();
            }

            @Override
            public void onCameraFailed(CameraInfo cameraInfo, int code, String error) {
                failed.incrementAndGet();
                firstError.compareAndSet(null, cameraInfo.getDeviceName() + ": " + error);
                complete();
            }

            private void complete() {
                if (remaining.decrementAndGet() == 0) {
                    int nFailed = failed.get();
                    if (nFailed == 0) {
                        event.onSuccess();
                    } else {
                        event.onFailed(-1, nFailed + "/" + total + " cameras failed, first error - " + firstError.get());
                    }
                }
            }
        });
    }

    /**
     * Apply an action to a specific list of cameras in parallel
     * @param cameras list of cameras to act on
     * @param whatToDo action to apply to each camera
     * @param perCameraCallback optional callback invoked per-camera on completion (may be null)
     */
    private void doSomethingOnCameras(List<CameraInfo> cameras, IDoSomething whatToDo, ICameraOperationCallback perCameraCallback) {
        for (CameraInfo cameraInfo : cameras) {
            MeariDeviceController deviceController = new MeariDeviceController();
            deviceController.setCameraInfo(cameraInfo);

            MeariUser.getInstance().setCameraInfo(cameraInfo);
            MeariUser.getInstance().setController(deviceController);

            try {
                doSomethingOnCamera(cameraInfo, whatToDo, perCameraCallback);
            } catch (Exception e) {
                // e.g. NPE inside the SDK for cameras with no cached device params:
                // report the failure instead of losing the per-camera completion
                Log.w("CamManager", "--->camera " + cameraInfo.getDeviceName() + " dispatch failed", e);
                if (perCameraCallback != null) {
                    perCameraCallback.onCameraFailed(cameraInfo, -1, e.toString());
                }
            }
        }
    }

    private void doSomethingOnCamera(CameraInfo cameraInfo, IDoSomething whatToDo, ICameraOperationCallback perCameraCallback) {
            whatToDo.doSomething(new ISetDeviceParamsCallback() {
                @Override
                public void onSuccess() {
                    Log.d("CamManager", "--->camera " + cameraInfo.getDeviceName() + " camera configuration success");
                    toast(whatToDo.description() + " on " + cameraInfo.getDeviceName());
                    if (perCameraCallback != null) {
                        perCameraCallback.onCameraSuccess(cameraInfo);
                    }
                }

                @Override
                public void onFailed(int i, String s) {
                    Log.w("CamManager", "--->camera " + cameraInfo.getDeviceName() + " camera configuration failed " + s);
                    toast("Failed on " + cameraInfo.getDeviceName() + " : " + s);
                    if (perCameraCallback != null) {
                        perCameraCallback.onCameraFailed(cameraInfo, i, s);
                    }
                }
            });
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
        Log.d("CamManager", "downloadAlertImagePreviews DeviceList " + list);
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
            Log.d("CamManager","Failed to download preview",fnfe);
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
