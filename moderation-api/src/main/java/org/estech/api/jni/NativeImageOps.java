package org.estech.api.jni;

import java.nio.ByteBuffer;

public class NativeImageOps {
    private static boolean libraryLoaded = false;

    static {
        try {
            NativeLibraryLoader.load("imageops");
            libraryLoaded = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static ByteBuffer preprocessToCHW(
            ByteBuffer encodedImage, int outW, int outH,
            float mean0, float mean1, float mean2,
            float std0, float std1, float std2) {

        if (!libraryLoaded) {
            throw new IllegalStateException("Native library not loaded");
        }

        if (encodedImage == null || !encodedImage.isDirect()) {
            throw new IllegalArgumentException("encodedImage must be a direct ByteBuffer");
        }

        return preprocessToCHWNative(encodedImage, outW, outH, mean0, mean1, mean2, std0, std1, std2);
    }

    public static void freeBuffer(ByteBuffer buf) {
        if (!libraryLoaded) {
            return; 
        }

        if (buf != null && buf.isDirect()) {
            freeBufferNative(buf);
        }
    }

    private static native ByteBuffer preprocessToCHWNative(
            ByteBuffer encodedImage, int outW, int outH,
            float mean0, float mean1, float mean2,
            float std0, float std1, float std2
    );

    private static native void freeBufferNative(ByteBuffer buf);
}