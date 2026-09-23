package online.avogadro.mearitaskerplugin.device;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import com.meari.sdk.MeariDeviceController;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.listener.MeariDeviceListener;
import com.ppstrong.ppsplayer.PPSGLSurfaceView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import online.avogadro.mearitaskerplugin.CommonUtils;

/**
 * Takes one live, full-resolution snapshot from a camera without any UI.
 *
 * Sequence (all SDK calls are issued on the main looper):
 * 1. P2P connect. For battery cameras the native connect itself sends the "awaken" request,
 *    so no REST wake-up is needed (the old /openapi/device/awaken + /status polling is
 *    rejected by the server since the 2026-09 auth change). Retried a few times.
 * 2. Preview on the highest-resolution bps2 stream (e.g. 102 = 2304x1296) with HARDWARE
 *    DECODING DISABLED for this controller: in soft mode the native ffmpeg decoder only
 *    memcpy's YUV into the renderer buffers, so an off-screen PPSGLSurfaceView is enough.
 * 3. Wait a couple of seconds after the first frame (exposure settles after wake-up), then
 *    snapshot(): in soft mode the native FFmpegPlayer::take_snapshot encodes the last decoded
 *    frame to JPEG at full stream resolution (no OpenGL involved).
 * 4. The JPEG is written to the app cache, then published to Pictures via MediaStore.
 *
 * The listener is invoked exactly once: onSuccess(absolute path, or content URI if the path
 * is not resolvable) or onFailed(message). A watchdog bounds the whole operation.
 */
class LiveSnapshotTaker {

    private static final String TAG = "TakePicture";

    /** Whole operation budget: must stay below the Tasker runner wait (55s) and host timeout (60s). */
    static final long TOTAL_TIMEOUT_MS = 50_000;
    private static final int CONNECT_ATTEMPTS = 3;
    private static final long CONNECT_RETRY_DELAY_MS = 3_000;
    /** Delay between the first decoded frame and the snapshot, lets auto-exposure settle. */
    private static final long SETTLE_AFTER_FIRST_FRAME_MS = 2_000;
    /** The native snapshot callback may precede the file being fully flushed. */
    private static final long FILE_WAIT_MS = 3_000;

    /** A camera accepts one P2P session from us at a time: serialize snapshots. */
    private static final AtomicBoolean IN_PROGRESS = new AtomicBoolean(false);

    private final Context context;
    private final CameraInfo camera;
    private final MeariDeviceListener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final long startedAt = SystemClock.elapsedRealtime();

    private MeariDeviceController controller;
    private PPSGLSurfaceView surface;
    private boolean connected = false;
    private boolean previewing = false;
    private File cacheFile;

    private LiveSnapshotTaker(Context context, CameraInfo camera, MeariDeviceListener listener) {
        this.context = context.getApplicationContext();
        this.camera = camera;
        this.listener = listener;
    }

    static void take(Context context, CameraInfo camera, MeariDeviceListener listener) {
        if (!IN_PROGRESS.compareAndSet(false, true)) {
            listener.onFailed("Another live snapshot is already in progress");
            return;
        }
        LiveSnapshotTaker taker = new LiveSnapshotTaker(context, camera, listener);
        taker.main.post(taker::start);
    }

    private void log(String msg) {
        Log.i(TAG, "[" + camera.getDeviceName() + " +" + (SystemClock.elapsedRealtime() - startedAt) + "ms] " + msg);
    }

    private void start() {
        main.postDelayed(() -> fail("Timeout after " + TOTAL_TIMEOUT_MS / 1000 + "s"), TOTAL_TIMEOUT_MS);
        try {
            controller = new MeariDeviceController();
            controller.setCameraInfo(camera);
            // Per-controller switch (not the global PPSMediaCodec flag, which the in-app live
            // view relies on): forces the native ffmpeg decoder, required by the off-screen
            // rendering and by the native JPEG snapshot.
            controller.enableHardDecode(false);
            // Created on the main thread: it is a View (SurfaceView) and never gets attached.
            surface = new PPSGLSurfaceView(context, 320, 240);
            cacheFile = new File(context.getCacheDir(), "snapshot_" + System.currentTimeMillis() + ".jpg");
        } catch (Throwable t) {
            Log.e(TAG, "setup failed", t);
            fail("Setup failed: " + t);
            return;
        }
        log("start: sn=" + camera.getSnNum() + " bps2=" + camera.getBps2() + " vst=" + camera.getVst());
        connect(1);
    }

    private void connect(int attempt) {
        if (finished.get()) return;
        log("connect attempt " + attempt + "/" + CONNECT_ATTEMPTS);
        controller.startConnect(new MeariDeviceListener() {
            @Override
            public void onSuccess(String msg) {
                main.post(() -> {
                    if (finished.get()) return;
                    connected = true;
                    log("connected: " + msg);
                    startPreview();
                });
            }

            @Override
            public void onFailed(String msg) {
                main.post(() -> {
                    if (finished.get()) return;
                    log("connect failed: " + msg);
                    if (attempt >= CONNECT_ATTEMPTS) {
                        fail("P2P connection failed: " + msg);
                        return;
                    }
                    controller.stopConnect(NOOP);
                    main.postDelayed(() -> connect(attempt + 1), CONNECT_RETRY_DELAY_MS);
                });
            }
        });
    }

    private void startPreview() {
        int streamId = Integer.parseInt(CommonUtils.getMaxResolutionStreamId(camera));
        log("startPreview on stream " + streamId);
        previewing = true;
        controller.startPreview(surface, streamId, new MeariDeviceListener() {
            @Override
            public void onSuccess(String msg) {
                // Native thread, fired once on the first decoded frame
                main.post(() -> {
                    if (finished.get()) return;
                    log("first frame: " + msg + " decodeMode=" + decodeModeName()
                            + " video=" + surface.renderer.videoWidth + "x" + surface.renderer.videoHeight);
                    main.postDelayed(LiveSnapshotTaker.this::snapshot, SETTLE_AFTER_FIRST_FRAME_MS);
                });
            }

            @Override
            public void onFailed(String msg) {
                main.post(() -> fail("Live preview failed: " + msg));
            }
        }, code -> main.post(() -> fail("Live video closed by the camera (code " + code + ")")));
    }

    private String decodeModeName() {
        PPSGLSurfaceView.PPSRenderer r = surface.renderer;
        if (r == null) return "released";
        return r.decode_mode == r.SOFT_DECODE_MODE ? "soft" : "HARD";
    }

    private void snapshot() {
        if (finished.get()) return;
        PPSGLSurfaceView.PPSRenderer r = surface.renderer;
        // In hard mode snapshot() waits for an OpenGL draw that never happens off-screen
        if (r == null || r.decode_mode != r.SOFT_DECODE_MODE) {
            fail("Live video is not software-decoded (" + decodeModeName() + "), cannot snapshot off-screen");
            return;
        }
        log("snapshot -> " + cacheFile.getAbsolutePath());
        controller.snapshot(cacheFile.getAbsolutePath(), new MeariDeviceListener() {
            @Override
            public void onSuccess(String msg) {
                log("snapshot callback success: " + msg);
                // Leave the main looper free: file wait + MediaStore copy are blocking
                new Thread(LiveSnapshotTaker.this::publish, "TakePicturePublish").start();
            }

            @Override
            public void onFailed(String msg) {
                main.post(() -> fail("Snapshot failed: " + msg));
            }
        });
    }

    private void publish() {
        long deadline = SystemClock.elapsedRealtime() + FILE_WAIT_MS;
        while (cacheFile.length() == 0 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100);
        }
        if (cacheFile.length() == 0) {
            main.post(() -> fail("Snapshot reported success but no image was written"));
            return;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), bounds);
        log("jpeg " + bounds.outWidth + "x" + bounds.outHeight + ", " + cacheFile.length() + " bytes");

        String result;
        try {
            result = saveToPictures(cacheFile);
        } catch (Exception e) {
            Log.e(TAG, "publish failed", e);
            main.post(() -> fail("Cannot save the picture: " + e.getMessage()));
            return;
        }
        log("saved: " + result);
        main.post(() -> succeed(result));
    }

    /** Copies the JPEG into the public Pictures folder, registered in the gallery. */
    private String saveToPictures(File jpeg) throws IOException {
        String name = "CloudEdge4TaskerSnapshot_" + camera.getDeviceID() + "_"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore insert returned null");
            try (InputStream in = new FileInputStream(jpeg);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open " + uri);
                copy(in, out);
            } catch (IOException e) {
                context.getContentResolver().delete(uri, null, null);
                throw e;
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            String path = queryPath(uri);
            return path != null ? path : uri.toString();
        }

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File dest = new File(dir, name);
        try (InputStream in = new FileInputStream(jpeg); OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
        }
        MediaScannerConnection.scanFile(context, new String[]{dest.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
        return dest.getAbsolutePath();
    }

    private String queryPath(Uri uri) {
        // DATA is deprecated but still readable, and Tasker/MacroDroid users need a file path
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{MediaStore.Images.Media.DATA}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception e) {
            Log.w(TAG, "cannot resolve path of " + uri, e);
        }
        return null;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private void succeed(String path) {
        if (!finished.compareAndSet(false, true)) return;
        cleanup();
        listener.onSuccess(path);
    }

    private void fail(String msg) {
        if (!finished.compareAndSet(false, true)) return;
        log("FAILED: " + msg);
        cleanup();
        listener.onFailed(msg);
    }

    /** Main looper only. */
    private void cleanup() {
        main.removeCallbacksAndMessages(null);
        if (controller != null) {
            try {
                if (previewing) controller.stopPreview(NOOP);
                if (connected || previewing) controller.stopConnect(NOOP);
            } catch (Throwable t) {
                Log.w(TAG, "cleanup", t);
            }
        }
        if (cacheFile != null) cacheFile.delete();
        IN_PROGRESS.set(false);
        log("cleanup done");
    }

    // The SDK invokes these callbacks without null checks
    private static final MeariDeviceListener NOOP = new MeariDeviceListener() {
        @Override public void onSuccess(String msg) {}
        @Override public void onFailed(String msg) {}
    };
}
