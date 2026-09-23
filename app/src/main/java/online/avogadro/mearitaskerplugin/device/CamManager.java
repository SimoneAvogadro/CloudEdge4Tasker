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
import com.meari.sdk.VideoInfo;
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
                // The camera was set on MeariUser by the caller (doSomethingOnCameras).
                MeariOpenApi.setIotConfig(MeariUser.getInstance().getCameraInfo(),
                        MeariOpenApi.IOT_PIR_DET_ENABLE, enableFlag, then);
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
                MeariOpenApi.setIotConfig(MeariUser.getInstance().getCameraInfo(),
                        MeariOpenApi.IOT_SOUND_LIGHT_ENABLE, enableFlag, then);
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
     * Live snapshot at the camera's highest resolution, saved to Pictures.
     * event.onSuccess receives the saved file path; every failure (login, unknown camera,
     * P2P, preview, snapshot, timeout) is reported through event.onFailed.
     * See {@link LiveSnapshotTaker} for the sequence.
     */
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
                LiveSnapshotTaker.take(context, cameraInfo, event);
            }

            @Override
            public String description() {
                return "Take a picture";
            }
        }, new ISetDeviceParamsCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailed(int code, String msg) {
                event.onFailed(msg);
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
                MeariOpenApi.setIotConfig(cameraInfo, MeariOpenApi.IOT_PIR_DET_ENABLE, enableFlag, event);
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
                MeariOpenApi.setIotConfig(cameraInfo, MeariOpenApi.IOT_SOUND_LIGHT_ENABLE, enableFlag, event);
            }
            @Override
            public String description() {
                return enableFlag == 1 ? "Enable camera alarm" : "Disable camera alarm";
            }
        });
    }

    /**
     * Run a command on each camera as soon as it is awake (see {@link AwakeCameraAction}),
     * all cameras in parallel. Shared by the Tasker actions and the in-app buttons.
     * @param event optional: invoked once, after ALL cameras answered (see
     *              {@link #reportWhenAllDone}); null for fire-and-forget with per-camera toasts
     */
    private void wakeAndRunOnCameras(List<CameraInfo> cameras, String description,
                                     AwakeCameraAction.Command command, ISetDeviceParamsCallback event) {
        if (cameras.isEmpty()) {
            if (event != null) event.onFailed(-1, "No cameras matched");
            return;
        }
        ICameraOperationCallback perCamera = event == null ? null : reportWhenAllDone(cameras.size(), event);
        for (CameraInfo cameraInfo : cameras) {
            AwakeCameraAction.run(cameraInfo, description, command,
                    perCameraReporter(cameraInfo, description, perCamera));
        }
    }

    public void fireSirenOnCameras(List<CameraInfo> cameras, ISetDeviceParamsCallback event) {
        wakeAndRunOnCameras(cameras, "Fire siren alarm", (cam, cb) ->
                MeariOpenApi.setIotConfig(cam, MeariOpenApi.IOT_SIREN_SWITCH, 1, cb), event);
    }

    public void turnOnLightOnCameras(List<CameraInfo> cameras, ISetDeviceParamsCallback event) {
        wakeAndRunOnCameras(cameras, "Turn on camera light", (cam, cb) ->
                MeariOpenApi.setIotConfig(cam, MeariOpenApi.IOT_LIGHT_SWITCH, 1, cb), event);
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
        doSomethingOnCameras(cameras, whatToDo, reportWhenAllDone(cameras.size(), event));
    }

    /**
     * Per-camera callback that invokes event exactly once, when all {@code total} cameras
     * have answered: onSuccess() if every camera succeeded, onFailed() with a summary otherwise.
     */
    private static ICameraOperationCallback reportWhenAllDone(int total, ISetDeviceParamsCallback event) {
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicInteger failed = new AtomicInteger(0);
        final AtomicReference<String> firstError = new AtomicReference<>();
        return new ICameraOperationCallback() {
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
        };
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
        whatToDo.doSomething(perCameraReporter(cameraInfo, whatToDo.description(), perCameraCallback));
    }

    /** Logs + toasts the outcome of an operation on one camera and forwards it to perCameraCallback (nullable). */
    private ISetDeviceParamsCallback perCameraReporter(CameraInfo cameraInfo, String description,
                                                       ICameraOperationCallback perCameraCallback) {
        return new ISetDeviceParamsCallback() {
            @Override
            public void onSuccess() {
                Log.d("CamManager", "--->camera " + cameraInfo.getDeviceName() + " camera configuration success");
                toast(description + " on " + cameraInfo.getDeviceName());
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
        };
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

    /**
     * Download the video of the most recent alert that has one, searching up to 10 days back.
     * Alert videos are cloud-hosted HLS .ts segments listed in the same /v3/app/event/list
     * response used for alert images; events without a cloud event recording have an empty list.
     * Will return the downloaded video's local path in CameraInfo.firmID (same hack as images).
     *
     * @param cameraID
     * @param res
     */
    public void getLastAlertVideo(long cameraID, IDeviceAlarmMessagesCallback res) {
        getLastAlertVideo(new Date(), cameraID, res);
    }

    public void getLastAlertVideo(Date date, long cameraID, IDeviceAlarmMessagesCallback res) {
        if (Util.getDaysBetween(new Date(), date) > 10) { // don't look more than 10 days back
            res.onError(-1, "no alert video available in the last 10 days");
            return;
        }

        SimpleDateFormat DateFor = new SimpleDateFormat("yyyyMMdd");
        String dateNow = DateFor.format(date);
        MeariUser.getInstance().getAlertMsgWithVideo(cameraID, dateNow, "1", 1, 0, null, new IDeviceAlarmMessagesCallback() {
            @Override
            public void onSuccess(List<DeviceAlarmMessage> list, CameraInfo cameraInfo) {
                Log.d("CamManager", "getLastAlertVideo " + dateNow + ": " + list.size() + " events, cloudStatus="
                        + cameraInfo.getCloudStatus() + " cst=" + cameraInfo.getCst() + " evt=" + cameraInfo.getEvt());
                DeviceAlarmMessage latest = latestMessageWithVideo(list);
                if (latest == null) {
                    getLastAlertVideo(Util.sendDateBackOneDay(date), cameraID, res);
                } else {
                    Log.d("CamManager", "getLastAlertVideo event " + latest.getEventTime() + ": "
                            + latest.getVideoUrl().size() + " segments, duration=" + latest.getVideoDuration()
                            + "s, storageType=" + latest.getStorageType() + " cloudType=" + latest.getCloudType());
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // the callback MUST always fire, or the blocking runner times out
                            try {
                                String path = downloadAlertVideo(latest.getVideoUrl(), cameraInfo.getSnNum());
                                if (path == null) {
                                    res.onError(-1, "failed to download alert video");
                                } else {
                                    cameraInfo.setFirmID(path);
                                    res.onSuccess(new ArrayList<DeviceAlarmMessage>(), cameraInfo);
                                }
                            } catch (Throwable t) {
                                Log.e("CamManager", "downloadAlertVideo failed", t);
                                res.onError(-1, "failed to download alert video: " + t.getMessage());
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onError(int i, String s) {
                res.onError(i, s);
            }
        });
    }

    private static DeviceAlarmMessage latestMessageWithVideo(List<DeviceAlarmMessage> list) {
        DeviceAlarmMessage latest = null;
        for (DeviceAlarmMessage m : list) {
            if (m.getVideoUrl() == null || m.getVideoUrl().isEmpty())
                continue;
            if (latest == null || parseEventTime(m) > parseEventTime(latest))
                latest = m;
        }
        return latest;
    }

    private static long parseEventTime(DeviceAlarmMessage m) {
        try {
            return Long.parseLong(m.getEventTime());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ffmpegCmd is a single native command-line entry point with global state:
    // never run two invocations concurrently
    private static final Object FFMPEG_LOCK = new Object();

    /**
     * Download and decrypt an alert video (list of HLS .ts segment URLs) into a public
     * Movies/*.mp4 file via the SDK's bundled ffmpeg. Blocking: call from a background thread.
     * Returns the local mp4 path, or null on failure.
     */
    static String downloadAlertVideo(List<VideoInfo> segments, String cameraSN) {
        Context ctx = MeariApplication.getInstance();
        String m3u8Path = new File(ctx.getCacheDir(),
                "CloudEdge4TaskerAlert" + System.currentTimeMillis() + ".m3u8").getAbsolutePath();
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!moviesDir.exists() && !moviesDir.mkdirs()) {
            Log.e("CamManager", "downloadAlertVideo: cannot create " + moviesDir);
            return null;
        }
        String mp4Path = new File(moviesDir,
                "CloudEdge4TaskerAlert" + System.currentTimeMillis() + ".mp4").getAbsolutePath();

        // Cloud media is normally encrypted with the licence id derived from the camera SN
        // (the same key DeviceCloudPlayActivity feeds the cloud player); retry without key
        // for unencrypted setups. On failure the SDK deletes the mp4 but keeps the m3u8,
        // so the playlist can be reused across attempts.
        String licence = (cameraSN == null || cameraSN.isEmpty()) ? "" : SdkUtils.formatLicenceId(cameraSN);
        String[] decKeys = licence.isEmpty() ? new String[]{""} : new String[]{licence, ""};
        for (String decKey : decKeys) {
            if (!new File(m3u8Path).exists()) {
                SdkUtils.getM3U8Path(segments, m3u8Path); // the SDK deletes the playlist on rc==0
            }
            int rc;
            synchronized (FFMPEG_LOCK) {
                rc = SdkUtils.downloadMp4FromM3U8(m3u8Path, mp4Path, decKey);
            }
            File mp4 = new File(mp4Path);
            Log.d("CamManager", "downloadAlertVideo ffmpeg rc=" + rc + " size=" + mp4.length()
                    + " decKey=" + (decKey.isEmpty() ? "none" : "licenceId"));
            if (rc == 0 && mp4.length() > 0) {
                MediaScannerConnection.scanFile(ctx, new String[]{mp4Path}, new String[]{"video/mp4"}, null);
                return mp4Path;
            }
            mp4.delete(); // rc==0 with empty file: don't leave the stub around for the next attempt
        }
        new File(m3u8Path).delete();
        return null;
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
