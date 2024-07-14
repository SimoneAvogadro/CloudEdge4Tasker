package com.ppstrong.utils;

public class MeariMediaUtil {
    // public static native int checkVideoPwd(String str, String str2);

    public static native int decodePic(String str, String str2, byte[] bArr);

    // public static native MrNativeAlarmImage[] parseMrAvAlarmImages(byte[] bArr);

    static {
        System.loadLibrary("mrplayer");
    }
}
