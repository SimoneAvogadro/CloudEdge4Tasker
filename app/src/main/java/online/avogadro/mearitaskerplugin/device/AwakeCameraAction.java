package online.avogadro.mearitaskerplugin.device;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.callback.ISetDeviceParamsCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a command (siren, light...) on a camera as soon as it is really awake.
 *
 * Instead of a blind wake-up + fixed 10s sleep, a {@link P2PSession} is opened: its native
 * connect wakes battery cameras and succeeds only once the camera answers (~3s in tests),
 * then the command is sent and the session closed. If the camera cannot be reached over P2P
 * within {@link #WAKE_TIMEOUT_MS}, the command is sent anyway (the server may still deliver
 * it), so this is never worse than the old approach. If this app already holds a session to
 * the camera (e.g. a live snapshot in progress), the camera is awake: the command is sent
 * immediately.
 *
 * The result callback is invoked exactly once, on the thread of the command's callback.
 */
class AwakeCameraAction {

    interface Command {
        void run(CameraInfo camera, ISetDeviceParamsCallback callback);
    }

    private static final String TAG = "AwakeCameraAction";

    /** Budget for the P2P wake-up: must leave room for login + command within the 45s Tasker runner wait. */
    static final long WAKE_TIMEOUT_MS = 20_000;

    private final CameraInfo camera;
    private final String description;
    private final Command command;
    private final ISetDeviceParamsCallback result;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean commandSent = new AtomicBoolean(false);
    private final long startedAt = SystemClock.elapsedRealtime();
    private P2PSession session;

    private AwakeCameraAction(CameraInfo camera, String description, Command command, ISetDeviceParamsCallback result) {
        this.camera = camera;
        this.description = description;
        this.command = command;
        this.result = result;
    }

    static void run(CameraInfo camera, String description, Command command, ISetDeviceParamsCallback result) {
        AwakeCameraAction action = new AwakeCameraAction(camera, description, command, result);
        action.main.post(action::start);
    }

    private void log(String msg) {
        Log.i(TAG, "[" + description + " on " + camera.getDeviceName() + " +"
                + (SystemClock.elapsedRealtime() - startedAt) + "ms] " + msg);
    }

    /** Main looper. */
    private void start() {
        session = P2PSession.open(camera, TAG);
        if (session == null) {
            log("camera already connected by this app, sending now");
            send();
            return;
        }
        main.postDelayed(() -> {
            log("not reachable over P2P within " + WAKE_TIMEOUT_MS / 1000 + "s, sending anyway");
            send();
        }, WAKE_TIMEOUT_MS);
        session.connect(new P2PSession.Listener() {
            @Override
            public void onConnected(P2PSession s) {
                log("awake");
                send();
            }

            @Override
            public void onFailed(String message) {
                log(message + ", sending anyway");
                send();
            }
        });
    }

    /** Main looper. Sends the command once, then releases the P2P session. */
    private void send() {
        if (!commandSent.compareAndSet(false, true)) return;
        main.removeCallbacksAndMessages(null);
        try {
            command.run(camera, new ISetDeviceParamsCallback() {
                @Override
                public void onSuccess() {
                    log("command OK");
                    closeSession();
                    result.onSuccess();
                }

                @Override
                public void onFailed(int code, String message) {
                    log("command failed: " + code + " " + message);
                    closeSession();
                    result.onFailed(code, message);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "command dispatch failed", e);
            closeSession();
            result.onFailed(-1, e.toString());
        }
    }

    private void closeSession() {
        main.post(() -> {
            if (session != null) session.close();
        });
    }
}
