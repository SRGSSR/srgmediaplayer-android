package ch.srg.mediaplayer.service.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * Created by zapek on 2014-11-28.
 */
public class AppUtils {

	public static String getApplicationName(Context context) {
		String s;
		PackageManager pm = context.getPackageManager();
		String packageName = context.getPackageName();
		ApplicationInfo ai;

		try
		{
			ai = pm.getApplicationInfo(packageName, 0);
			s = (String) pm.getApplicationLabel(ai);
		}
		catch (PackageManager.NameNotFoundException e)
		{
			s = "Player";
		}
		return (s);
	}
}
