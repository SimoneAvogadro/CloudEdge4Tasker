package online.avogadro.mearitaskerplugin;

import android.text.TextUtils;

import com.meari.sdk.bean.CameraInfo;
import com.meari.sdk.json.BaseJSONObject;

import org.json.JSONException;

public class CommonUtils {
    public static String getDefaultStreamId(CameraInfo cameraInfo) {
        String streamId = "1";
        if (cameraInfo.getVst() == 1) {
            streamId = "0";
        } else {
            if (!TextUtils.isEmpty(cameraInfo.getBps2())) {
                try {
                    BaseJSONObject object = new BaseJSONObject(cameraInfo.getBps2());
                    if (object.has("0")) {
                        streamId = "100";
                    } else if (object.has("1")) {
                        streamId = "101";
                    } else if (object.has("2")) {
                        streamId = "102";
                    } else if (object.has("3")) {
                        streamId = "103";
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (cameraInfo.getBps() == 0 || cameraInfo.getBps() == -1) {
                streamId = "0";
            } else {
                streamId = "1";
            }
        }
        return streamId;
    }

    /**
     * Stream ID with the highest resolution, for full-quality snapshots.
     * bps2 is a JSON like {"0":"640x360@15","2":"2304x1296@15"}: key k is stream 100+k
     * (100=SD, 101=HD, 102=FHD/QHD, 103=UHD in the official app). The native streams 0/1
     * are never requested by the official app on bps2 cameras, so they are not used here.
     * Falls back to {@link #getDefaultStreamId} when bps2 is missing or unparsable.
     */
    public static String getMaxResolutionStreamId(CameraInfo cameraInfo) {
        if (cameraInfo.getVst() != 1 && !TextUtils.isEmpty(cameraInfo.getBps2())) {
            try {
                BaseJSONObject object = new BaseJSONObject(cameraInfo.getBps2());
                int bestKey = -1;
                long bestArea = -1;
                for (int k = 0; k <= 4; k++) {
                    String key = String.valueOf(k);
                    if (!object.has(key)) continue;
                    long area = 0;
                    String[] wh = object.optString(key).split("@")[0].split("x");
                    if (wh.length == 2) {
                        try {
                            area = Long.parseLong(wh[0].trim()) * Long.parseLong(wh[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    // ties/unknown sizes: prefer the higher key (higher quality tier)
                    if (area >= bestArea) {
                        bestArea = area;
                        bestKey = k;
                    }
                }
                if (bestKey >= 0) {
                    return String.valueOf(100 + bestKey);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return getDefaultStreamId(cameraInfo);
    }

}
