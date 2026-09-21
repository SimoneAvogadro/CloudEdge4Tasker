package online.avogadro.mearitaskerplugin.device;

import android.util.Base64;
import android.util.Log;

import com.meari.sdk.MeariSmartSdk;
import com.meari.sdk.MeariUser;
import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.bean.DeviceParams;
import com.meari.sdk.bean.UserInfo;
import com.meari.sdk.callback.IGetDeviceParamsCallback;
import com.meari.sdk.callback.ISetDeviceParamsCallback;
import com.meari.sdk.common.ServerUrl;
import com.meari.sdk.http.RequestParams;
import com.meari.sdk.json.BaseJSONObject;
import com.meari.sdk.utils.JsonUtil;
import com.meari.sdk.utils.SdkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Replacement for the SDK's MeariIotManager get/setDeviceConfig calls.
 *
 * <p>Since ~Sept 2026 the Meari cloud rejects the openapi calls made by the bundled
 * 2023 SDK with {@code {"errid":401,"errstr":"Authorization Failed","reason":"STError"}}.
 * The server was hardened (fix for CVE-2026-33357, an IDOR on /openapi/device/*): the
 * global HMAC signature is no longer sufficient, the request must also be bound to the
 * specific device. The current official app sends three extra query parameters that the
 * 2023 SDK knows nothing about:
 *
 * <ul>
 *   <li>{@code token}    - the per-device {@code deviceSignature} returned by getDevice.action</li>
 *   <li>{@code t}        - the per-device {@code t} returned alongside it (expiry, ~3 days out)</li>
 *   <li>{@code clientid} - the logged-in userID</li>
 * </ul>
 *
 * <p>Everything else (the global HMAC-SHA1 signature, expires, params encoding) is
 * unchanged, so this class deliberately reuses the SDK's own public helpers
 * ({@link SdkUtils#getTimeOut()}, {@link SdkUtils#getSignature}, {@link SdkUtils#formatSn})
 * to stay byte-identical to what the official app sends.
 *
 * <p>The 2023 {@code CameraInfo}/{@code JsonUtil} do not parse {@code deviceSignature}/{@code t},
 * so this class fetches the raw device list itself and caches the pair per camera SN.
 *
 * <p>Threading: all work happens on a background executor and callbacks are invoked on
 * that worker thread, never on the main looper - {@code runBlockingCameraAction} blocks
 * its calling thread on a CountDownLatch, so posting to the main looper could deadlock.
 */
public class MeariOpenApi {

    private static final String TAG = "MeariOpenApi";

    private static final String PATH_DEVICE_CONFIG = "/openapi/device/config";

    /** IoT parameter codes, mirrored from com.meari.sdk.common.IotConstants. */
    public static final String IOT_PIR_DET_ENABLE     = "150"; // PirDetEnable
    public static final String IOT_SOUND_LIGHT_ENABLE = "182"; // soundLightEnable
    public static final String IOT_LIGHT_SWITCH       = "167"; // flightLightSwitch
    public static final String IOT_SIREN_SWITCH       = "823"; // flightSirenSwitch

    /**
     * How long a cached deviceSignature is reused. The server-issued token carries an
     * expiry ~3 days out, but it is re-issued on every device-list fetch; refreshing
     * every 30 min keeps it comfortably fresh without hammering the API.
     */
    private static final long SIG_TTL_MS = 30 * 60 * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS    = 20000;

    private static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MeariOpenApi");
        t.setDaemon(true);
        return t;
    });

    /** Per-device authorization material returned by getDevice.action. */
    private static final class DevSig {
        final String token;
        final String t;
        DevSig(String token, String t) { this.token = token; this.t = t; }
    }

    /** snNum -> signature. Populated by {@link #refreshSignatures()}. */
    private static final Map<String, DevSig> SIG_CACHE = new ConcurrentHashMap<>();
    private static volatile long sigCacheLoadedAt = 0L;

    private MeariOpenApi() { }

    // ---------------------------------------------------------------- public API

    /**
     * Set a single IoT parameter on a camera (PIR, siren, light, ...).
     * Replaces MeariUser.setPirDetectionEnable / setFloodCameraVoiceLightAlarmEnable /
     * setFlightSirenEnable / setFlightLightStatus, which all funnel into the SDK's
     * setDeviceConfig and therefore fail with 401 against the current server.
     */
    public static void setIotConfig(final CameraInfo cam, final String iotCode, final int value,
                                    final ISetDeviceParamsCallback callback) {
        EXEC.execute(() -> {
            try {
                if (cam == null) {
                    fail(callback, -1, "No camera");
                    return;
                }
                // Mirrors SDK setDeviceConfig(): codes in [800,900) except 825 go to the
                // device itself, everything else is applied on the server.
                boolean toServer = true;
                try {
                    int dp = Integer.parseInt(iotCode);
                    if (dp >= 800 && dp < 900 && dp != 825) toServer = false;
                } catch (NumberFormatException ignored) { }

                String params = "{\"code\":100001,\"action\":\"set\",\"name\":\"iot\",\"iot\":{"
                        + "\"" + iotCode + "\":" + value + "}}";

                String body = callConfig(cam, "set", params, toServer, true);
                // A successful set returns no "errid"
                JSONObject json = new JSONObject(body);
                if (json.has("errid")) {
                    fail(callback, json.optInt("errid"), json.optString("errstr", "openapi error"));
                } else {
                    if (callback != null) callback.onSuccess();
                }
            } catch (Exception e) {
                Log.w(TAG, "setIotConfig failed", e);
                fail(callback, -1, String.valueOf(e.getMessage()));
            }
        });
    }

    /**
     * Read all IoT parameters of a camera, returning the same {@link DeviceParams} the SDK
     * would have produced (parsing is delegated to the SDK's own JsonUtil).
     * Replaces MeariUser.getDeviceParams().
     */
    public static void getDeviceParams(final CameraInfo cam, final IGetDeviceParamsCallback callback) {
        EXEC.execute(() -> {
            try {
                if (cam == null) {
                    if (callback != null) callback.onFailed(-1, "No camera");
                    return;
                }
                String params = "{\"code\":100001,\"action\":\"get\",\"name\":\"iot\"}";
                String body = callConfig(cam, "get", params, true, true);

                JSONObject json = new JSONObject(body);
                if (json.has("errid")) {
                    if (callback != null) {
                        callback.onFailed(json.optInt("errid"), json.optString("errstr", "openapi error"));
                    }
                    return;
                }
                JSONObject iot = json.optJSONObject("iot");
                if (iot == null) {
                    if (callback != null) callback.onFailed(-1, "Return data exception");
                    return;
                }
                DeviceParams dp = JsonUtil.getDeviceParamsIot(new BaseJSONObject(iot.toString()));
                if (dp == null) {
                    if (callback != null) callback.onFailed(-1, "Parse json error");
                } else {
                    if (callback != null) callback.onSuccess(dp);
                }
            } catch (Exception e) {
                Log.w(TAG, "getDeviceParams failed", e);
                if (callback != null) callback.onFailed(-1, String.valueOf(e.getMessage()));
            }
        });
    }

    /** Drops the cached device signatures, forcing a refresh on the next call. */
    public static void invalidateSignatures() {
        SIG_CACHE.clear();
        sigCacheLoadedAt = 0L;
    }

    // ---------------------------------------------------------------- internals

    /**
     * Performs one GET /openapi/device/config, retrying once with fresh device signatures
     * if the server answers 401 (the per-device token may simply have gone stale).
     */
    private static String callConfig(CameraInfo cam, String action, String paramsJson,
                                     boolean toServer, boolean allowRetry) throws Exception {
        awaitOpenApiReady(); // also guarantees login finished before we fetch signatures
        DevSig sig = signatureFor(cam, false);
        String body = doConfigRequest(cam, action, paramsJson, toServer, sig);

        if (allowRetry && body != null && body.contains("\"errid\":401")) {
            String reason = "";
            try {
                reason = new JSONObject(body).optString("reason", "");
            } catch (Exception ignored) { }
            Log.w(TAG, "openapi 401 (" + reason + ") for " + cam.getDeviceName() + ", refreshing credentials and retrying");

            // "Timeout" means the global platform signature expired (it lives ~11h);
            // the SDK refreshes it through getIotInfoV2, so do the same.
            if ("Timeout".equals(reason)) {
                refreshIotInfo();
            }
            // Any other 401 (e.g. "STError") points at a stale per-device token.
            sig = signatureFor(cam, true);
            body = doConfigRequest(cam, action, paramsJson, toServer, sig);
        }
        return body;
    }

    /**
     * Re-fetches the global openapi credentials (accessid/accesskey) and blocks until done.
     * Safe to call from the worker thread only.
     */
    private static void refreshIotInfo() {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        try {
            MeariUser.getInstance().getIotInfoV2(new com.meari.sdk.callback.IResultCallback() {
                @Override public void onSuccess() { latch.countDown(); }
                @Override public void onError(int code, String error) {
                    Log.w(TAG, "getIotInfoV2 failed: " + code + " " + error);
                    latch.countDown();
                }
            }, 1);
            latch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.w(TAG, "getIotInfoV2 threw", e);
        }
    }

    /**
     * The openapi credentials (accessid/accesskey/openapi domain) are filled in
     * asynchronously by getIotInfoV2() shortly after login, so an action fired right after
     * a cold login can find them empty. DeviceListActivity waits the same way before
     * enabling its controls; here we block the worker thread (never the main one).
     */
    private static void awaitOpenApiReady() throws InterruptedException {
        final int maxAttempts = 30;      // ~15s, same order as DeviceListActivity
        final int intervalMs = 500;
        for (int i = 0; i < maxAttempts; i++) {
            if (notEmpty(MeariSmartSdk.meariPlatAccessId)
                    && notEmpty(MeariSmartSdk.meariPlatAccessKey)
                    && notEmpty(MeariSmartSdk.meariPlatOpenApiServer)) {
                return;
            }
            if (i == 0) Log.d(TAG, "waiting for openapi credentials...");
            Thread.sleep(intervalMs);
        }
        Log.w(TAG, "openapi credentials still not available after "
                + (maxAttempts * intervalMs / 1000) + "s");
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String doConfigRequest(CameraInfo cam, String action, String paramsJson,
                                          boolean toServer, DevSig sig) throws Exception {
        awaitOpenApiReady();
        String baseUrl = MeariSmartSdk.meariPlatOpenApiServer;
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("openapi server unknown (not logged in?)");
        }

        Map<String, String> q = new java.util.LinkedHashMap<>();
        // Same global signature scheme as the 2023 SDK - unchanged server-side.
        q.put("accessid", MeariSmartSdk.meariPlatAccessId);
        q.put("expires", SdkUtils.getTimeOut());
        q.put("signature", SdkUtils.getSignature(PATH_DEVICE_CONFIG, action, MeariSmartSdk.meariPlatAccessKey));
        q.put("action", action);
        q.put("deviceid", SdkUtils.formatSn(cam.getSnNum()));
        if (toServer) q.put("target", "server");

        // The three parameters the 2023 SDK is missing.
        if (sig != null) {
            if (sig.token != null && !sig.token.isEmpty()) q.put("token", sig.token);
            if (sig.t != null && !sig.t.isEmpty()) q.put("t", sig.t);
        }
        UserInfo user = MeariUser.getInstance().getUserInfo();
        if (user != null) q.put("clientid", String.valueOf(user.getUserID()));

        q.put("params", Base64.encodeToString(paramsJson.getBytes("UTF-8"), Base64.NO_WRAP));

        String url = baseUrl + PATH_DEVICE_CONFIG + "?" + encodeQuery(q);
        Log.d(TAG, "openapi " + action + " " + cam.getDeviceName() + " params=" + paramsJson);
        String body = httpGet(url);
        Log.d(TAG, "openapi " + action + " " + cam.getDeviceName() + " -> " + body);
        return body;
    }

    /**
     * Returns the per-device token/t pair, refreshing the whole device list if the cache
     * is empty, stale, or {@code force} is set.
     */
    private static synchronized DevSig signatureFor(CameraInfo cam, boolean force) {
        boolean stale = force
                || SIG_CACHE.isEmpty()
                || System.currentTimeMillis() > sigCacheLoadedAt + SIG_TTL_MS;
        if (stale) {
            try {
                refreshSignatures();
            } catch (Exception e) {
                Log.w(TAG, "could not refresh device signatures", e);
            }
        }
        DevSig sig = SIG_CACHE.get(cam.getSnNum());
        if (sig == null) {
            Log.w(TAG, "no deviceSignature cached for " + cam.getDeviceName() + " (" + cam.getSnNum() + ")");
        }
        return sig;
    }

    /**
     * Fetches the raw device list and harvests {@code deviceSignature} + {@code t} per camera.
     * The SDK's own getDeviceList() drops both fields, so the call is made directly here,
     * reusing the SDK's parameter/header builders so it is indistinguishable from the SDK's.
     */
    private static void refreshSignatures() throws Exception {
        String apiServer = MeariSmartSdk.apiServer;
        if (apiServer == null || apiServer.isEmpty()) {
            throw new IllegalStateException("apiServer unknown (not logged in?)");
        }
        RequestParams params = new RequestParams();
        params.put("deviceTypeID", "2");

        String url = apiServer + ServerUrl.API_PPSTRONG_HOME_CAMERA;
        String body = httpPostForm(url,
                params.getParams(),
                SdkUtils.getOKHttpHeader(ServerUrl.API_PPSTRONG_HOME_CAMERA));

        JSONObject json = new JSONObject(body);
        int found = 0;
        // Cameras live under "snap"; iterate every array so doorbells/NVRs work too.
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            JSONArray arr = json.optJSONArray(keys.next());
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject dev = arr.optJSONObject(i);
                if (dev == null) continue;
                String sn = dev.optString("snNum", "");
                String token = dev.optString("deviceSignature", "");
                String t = dev.has("t") ? String.valueOf(dev.opt("t")) : "";
                if (!sn.isEmpty() && !token.isEmpty()) {
                    SIG_CACHE.put(sn, new DevSig(token, t));
                    found++;
                }
            }
        }
        sigCacheLoadedAt = System.currentTimeMillis();
        Log.d(TAG, "refreshed device signatures for " + found + " devices");
        if (found == 0) {
            Log.w(TAG, "device list carried no deviceSignature - server may have changed again");
        }
    }

    private static void fail(ISetDeviceParamsCallback cb, int code, String msg) {
        if (cb != null) cb.onFailed(code, msg);
    }

    // ---------------------------------------------------------------- http

    private static String encodeQuery(Map<String, String> q) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
              .append('=')
              .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String httpPostForm(String url, Map<String, String> form,
                                       HashMap<String, String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }
            byte[] payload = encodeQuery(form).getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (Exception e) {
            in = conn.getErrorStream();
            if (in == null) throw e;
        }
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
