package com.billiards.sdk;

import android.graphics.Bitmap;
import android.graphics.PointF;

public class BilliardsSDK {

    private long nativeHandle = 0;

    public BilliardsSDK() {
        nativeHandle = nativeInit();
    }

    public ProcessingResult processFrame(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return nativeProcessFrame(nativeHandle, pixels, bitmap.getWidth(), bitmap.getHeight());
    }

    public void setTemplateBitmap(Bitmap template) {
        int[] pixels = new int[template.getWidth() * template.getHeight()];
        template.getPixels(pixels, 0, template.getWidth(), 0, 0, template.getWidth(), template.getHeight());
        nativeSetTemplate(nativeHandle, pixels, template.getWidth(), template.getHeight());
    }

    public void release() {
        if (nativeHandle != 0) {
            nativeRelease(nativeHandle);
            nativeHandle = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    private static native long nativeInit();
    private static native void nativeRelease(long handle);
    private static native void nativeSetTemplate(long handle, int[] pixels, int width, int height);
    private static native ProcessingResult nativeProcessFrame(long handle, int[] pixels, int width, int height);

    public static class ProcessingResult {
        public PointF templateCenter;
        public PointF aimPoint;
        public PointF whiteBall;
        public PointF targetBall;
        public float confidence;

        public ProcessingResult() {
            this.templateCenter = null;
            this.aimPoint = null;
            this.whiteBall = null;
            this.targetBall = null;
            this.confidence = 0f;
        }
    }
}
