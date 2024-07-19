package online.avogadro.mearitaskerplugin.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Util {

    public static String getMetadata(Context c, String key) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(),
                    PackageManager.GET_META_DATA);

            Bundle metaData = ai.metaData;

            return metaData.getString(key, "8");
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getMetadataInt(Context c, String key) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle metaData = ai.metaData;

            return metaData.getInt(key,8);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static long getDaysBetween(Date date1, Date date2) {
        long diffInMillies = Math.abs(date2.getTime() - date1.getTime());
        return TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    public static Date sendDateBackOneDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, -1);
        return cal.getTime();
    }
}
