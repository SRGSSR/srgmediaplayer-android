package ch.srg.mediaplayer;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ch.srg.mediaplayer.egl.EglCore;
import ch.srg.mediaplayer.egl.GlUtil;
import ch.srg.mediaplayer.egl.ScaledDrawable2d;
import ch.srg.mediaplayer.egl.Sprite2d;
import ch.srg.mediaplayer.egl.Texture2dProgram;


/**
 * Created by seb on 30/03/16.
 */
public class PlayerSurface {
    private static final String TAG = EglCore.TAG;
    // EglCore object we're associated with.  It may be associated with multiple surfaces.
    protected EglCore eglCore;

    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private int width = -1;
    private int height = -1;

    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private Texture2dProgram texture2dProgram;
    private ScaledDrawable2d scaledDrawable2d;
    private final Sprite2d mRect = new Sprite2d(scaledDrawable2d);

    protected PlayerSurface(EglCore eglCore) {
        this.eglCore = eglCore;
    }

    /**
     * Creates a window surface.
     * <p>
     * @param surface May be a Surface or SurfaceTexture.
     */
    public void createWindowSurface(Object surface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        eglSurface = eglCore.createWindowSurface(surface);

        // Don't cache width/height here, because the size of the underlying surface can change
        // out from under us (see e.g. HardwareScalerActivity).
        //this.width = eglCore.querySurface(eglSurface, EGL14.EGL_WIDTH);
        //this.height = eglCore.querySurface(eglSurface, EGL14.EGL_HEIGHT);
    }

    public void createSurfaceTexture(SurfaceTexture.OnFrameAvailableListener listener) {
        texture2dProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
        int textureId = texture2dProgram.createTextureObject();
        surfaceTexture = new SurfaceTexture(textureId);
        mRect.setTexture(textureId);

        surfaceTexture.setOnFrameAvailableListener(listener);
    }
    /**
     * Creates an off-screen surface.
     */
    public void createOffscreenSurface(int width, int height) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        eglSurface = eglCore.createOffscreenSurface(width, height);
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the surface's width, in pixels.
     * <p>
     * If this is called on a window surface, and the underlying surface is in the process
     * of changing size, we may not see the new size right away (e.g. in the "surfaceChanged"
     * callback).  The size should match after the next buffer swap.
     */
    public int getWidth() {
        if (this.width < 0) {
            return eglCore.querySurface(eglSurface, EGL14.EGL_WIDTH);
        } else {
            return this.width;
        }
    }

    /**
     * Returns the surface's height, in pixels.
     */
    public int getHeight() {
        if (this.height < 0) {
            return eglCore.querySurface(eglSurface, EGL14.EGL_HEIGHT);
        } else {
            return this.height;
        }
    }

    /**
     * Release the EGL surface.
     */
    public void releaseEglSurface() {
        eglCore.releaseSurface(eglSurface);
        eglSurface = EGL14.EGL_NO_SURFACE;
        this.width = this.height = -1;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        eglCore.makeCurrent(eglSurface);
    }

    /**∂
     * Makes our EGL context and surface current for drawing, using the supplied surface
     * for reading.
     */
    public void makeCurrentReadFrom(PlayerSurface readSurface) {
        eglCore.makeCurrent(eglSurface, readSurface.eglSurface);
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    public boolean swapBuffers() {
        boolean result = eglCore.swapBuffers(eglSurface);
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed");
        }
        return result;
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        eglCore.setPresentationTime(eglSurface, nsecs);
    }

    /**
     * Saves the EGL surface to a file.
     * <p>
     * Expects that this object's EGL surface is current.
     */
    public void saveFrame(File file) throws IOException {
        if (!eglCore.isCurrent(eglSurface)) {
            throw new RuntimeException("Expected EGL context/surface is not current");
        }

        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.

        String filename = file.toString();

        int width = getWidth();
        int height = getHeight();
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GlUtil.checkGlError("glReadPixels");
        buf.rewind();

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filename));
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }
        Log.d(TAG, "Saved " + width + "x" + height + " frame as '" + filename + "'");
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }
}
