package ch.srg.mediaplayer.utils;

import android.content.pm.ActivityInfo;
import android.util.DisplayMetrics;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Created by seb on 12/01/16.
 */
public class OrientationUtils {

    public static int getPhysicalOrientation(WindowManager windowManager, int physicalRotation) {
        if (physicalRotation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
        int surfaceRotation;
        physicalRotation += 45;
        physicalRotation %= 360;
        if (physicalRotation < 90) {
            surfaceRotation = Surface.ROTATION_0;
        } else if (physicalRotation < 180) {
            surfaceRotation = Surface.ROTATION_90;
        } else if (physicalRotation < 270) {
            surfaceRotation = Surface.ROTATION_180;
        } else {
            surfaceRotation = Surface.ROTATION_270;
        }
        int windowManagerRotation = windowManager.getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((windowManagerRotation == Surface.ROTATION_0
                || windowManagerRotation == Surface.ROTATION_180) && height > width ||
                (windowManagerRotation == Surface.ROTATION_90
                        || windowManagerRotation == Surface.ROTATION_270) && width > height) {
            switch (surfaceRotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (surfaceRotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                    break;
            }
        }

        return orientation;
    }

}
