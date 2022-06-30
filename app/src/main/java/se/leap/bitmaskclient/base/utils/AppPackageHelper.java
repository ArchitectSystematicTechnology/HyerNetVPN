package se.leap.bitmaskclient.base.utils;

import android.content.Context;
import android.content.pm.PackageManager;

public class AppPackageHelper {
    public static int getUidFromPackage(Context context, String packageName) {
        int uid = 0;
        try {
            uid = context.getPackageManager().getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return uid;
    }
}
