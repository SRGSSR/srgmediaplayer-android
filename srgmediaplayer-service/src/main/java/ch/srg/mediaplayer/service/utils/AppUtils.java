package ch.srg.mediaplayer.service.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.TypedValue;

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

	public static Bitmap scaleAndFitBitmap(Bitmap source, int newHeight, int newWidth) {
		if (source == null) {
			return null;
		}
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();

		float xScale = (float) newWidth / sourceWidth;
		float yScale = (float) newHeight / sourceHeight;
		float scale = Math.min(xScale, yScale);

		float scaledWidth = scale * sourceWidth;
		float scaledHeight = scale * sourceHeight;

		float left = (newWidth - scaledWidth) / 2;
		float top = (newHeight - scaledHeight) / 2;

		RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

		Bitmap destination = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
		Canvas canvas = new Canvas(destination);
		canvas.drawBitmap(source, null, targetRect, null);

		return destination;
	}

	public static int convertDpToPixel(Context context, float dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
				context.getResources().getDisplayMetrics());
	}
}
