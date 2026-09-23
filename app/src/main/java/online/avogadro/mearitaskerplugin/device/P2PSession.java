package online.avogadro.mearitaskerplugin.device;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.listener.MeariDeviceListener;

import java.util.HashSet;
import java.util.Set;

/**
 * One P2P connection from this app to a camera, with retries.
 *
 * The native connect also wakes battery cameras (it sends an "awaken" to the Meari server
 * before dialing), so a successful connect is the most reliable "camera is awake" signal
 * available: the REST wake-up/status endpoints of the 2023 SDK are rejected by the server
 * since the 2026-09 auth change.
 *
 * Main looper only: every method must be called there and the listener is invoked there,
 * exactly once. A camera accepts a single session from us: {@link #open} returns null while
 * another session to the same camera is active.
 */
class P2PSession {

    interface Listener {
        void onConnected(P2PSession session);
        void onFailed(String message);
    }

    private static final int CONNECT_ATTEMPTS = 3;
    private static final long CONNECT_RETRY_DELAY_MS = 3_000;

    /** Serial numbers of the cameras with an active session (main looper only). */
    private static final Set<String> ACTIVE = new HashSet<>();

    private final CameraInfo camera;
    private final String tag;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MeariDeviceController controller = new MeariDeviceController();
    private boolean connected = false;
    private boolean previewing = false;
    private boolean closed = false;

    private P2PSession(CameraInfo camera, String tag) {
        this.camera = camera;
        this.tag = tag;
        controller.setCameraInfo(camera);
    }

    /** @return a new session, or null if this camera already has an active session. */
    static P2PSession open(CameraInfo camera, String tag) {
        if (!ACTIVE.add(camera.getSnNum())) {
            return null;
        }
        return new P2PSession(camera, tag);
    }

    MeariDeviceController controller() {
        return controller;
    }

    /** Remembers that a preview was started, so {@link #close} stops it first. */
    void setPreviewing(boolean previewing) {
        this.previewing = previewing;
    }

    void connect(Listener listener) {
        connect(1, listener);
    }

    private void connect(int attempt, Listener listener) {
        if (closed) return;
        Log.i(tag, "[" + camera.getDeviceName() + "] connect attempt " + attempt + "/" + CONNECT_ATTEMPTS);
        controller.startConnect(new MeariDeviceListener() {
            @Override
            public void onSuccess(String msg) {
                main.post(() -> {
                    if (closed) return;
                    connected = true;
                    Log.i(tag, "[" + camera.getDeviceName() + "] connected: " + msg);
                    listener.onConnected(P2PSession.this);
                });
            }

            @Override
            public void onFailed(String msg) {
                main.post(() -> {
                    if (closed) return;
                    Log.i(tag, "[" + camera.getDeviceName() + "] connect failed: " + msg);
                    if (attempt >= CONNECT_ATTEMPTS) {
                        listener.onFailed("P2P connection failed: " + msg);
                        return;
                    }
                    controller.stopConnect(NOOP);
                    main.postDelayed(() -> connect(attempt + 1, listener), CONNECT_RETRY_DELAY_MS);
                });
            }
        });
    }

    /** Idempotent: stops preview and connection, frees the camera for other sessions. */
    void close() {
        if (closed) return;
        closed = true;
        main.removeCallbacksAndMessages(null);
        try {
            if (previewing) controller.stopPreview(NOOP);
            if (connected || previewing) controller.stopConnect(NOOP);
        } catch (Throwable t) {
            Log.w(tag, "close", t);
        }
        ACTIVE.remove(camera.getSnNum());
    }

    // The SDK invokes these callbacks without null checks
    static final MeariDeviceListener NOOP = new MeariDeviceListener() {
        @Override public void onSuccess(String msg) {}
        @Override public void onFailed(String msg) {}
    };
}
