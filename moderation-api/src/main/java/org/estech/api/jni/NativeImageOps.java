package org.estech.api.jni;


import java.nio.ByteBuffer;
import java.util.Arrays;

public class NativeImageOps {

    static {
        try {
            NativeLibraryLoader.load("imageops");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }

    /**
     * 预处理图像为 CHW 格式 (C, H, W)
     *
     * @param encodedImage 编码的图像数据 (JPEG/PNG) 的 DirectByteBuffer
     * @param outW 输出宽度
     * @param outH 输出高度
     * @param mean0 R 通道均值
     * @param mean1 G 通道均值
     * @param mean2 B 通道均值
     * @param std0 R 通道标准差
     * @param std1 G 通道标准差
     * @param std2 B 通道标准差
     * @return float 数组，格式为 [C, H, W]，大小为 3 * outH * outW
     */
    public native float[] preprocessToCHWNative(
            ByteBuffer encodedImage,
            int outW, int outH,
            float mean0, float mean1, float mean2,
            float std0, float std1, float std2
    );

    /**
     * 便捷方法：使用 ImageNet 标准归一化参数
     */
    public float[] preprocessImageNetCHW(ByteBuffer encodedImage, int size) {
        if (encodedImage == null || !encodedImage.isDirect()) {
            throw new IllegalArgumentException("encodedImage must be a direct ByteBuffer");
        }

        return preprocessToCHWNative(
                encodedImage,
                size, size,
                0.485f, 0.456f, 0.406f,  // ImageNet mean
                0.229f, 0.224f, 0.225f   // ImageNet std
        );
    }

    /**
     * 验证并打印数组统计信息
     */
    public static void validateAndPrintStats(float[] data, String label) {
        if (data == null || data.length == 0) {
            return;
        }

        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        double sum = 0;
        int nanCount = 0;
        int infCount = 0;

        for (float v : data) {
            if (Float.isNaN(v)) {
                nanCount++;
                continue;
            }
            if (Float.isInfinite(v)) {
                infCount++;
                continue;
            }
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }

        double mean = sum / (data.length - nanCount - infCount);
    }
}