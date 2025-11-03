#include <jni.h>
#include <cstdlib>
#include <cmath>
#include <cstring>
#include <cstdio>

#define STB_IMAGE_IMPLEMENTATION
#include "third_party/stb_image.h"

#define STB_IMAGE_RESIZE2_IMPLEMENTATION
#include "third_party/stb_image_resize2.h"

#include "org_estech_api_jni_NativeImageOps.h"

static void center_crop_rgb_u8(const unsigned char* src, int rw, int rh,
                               unsigned char* dst, int outW, int outH) {
    int x0 = (rw - outW) / 2;
    int y0 = (rh - outH) / 2;
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;

    for (int y = 0; y < outH; ++y) {
        int sy = y0 + y;
        const unsigned char* srow = src + (sy * rw + x0) * 3;
        unsigned char* drow = dst + (y * outW) * 3;
        std::memcpy(drow, srow, (size_t)outW * 3);
    }
}

JNIEXPORT jfloatArray JNICALL Java_org_estech_api_jni_NativeImageOps_preprocessToCHWNative
  (JNIEnv* env, jobject,
   jobject encodedImage, jint outW, jint outH,
   jfloat mean0, jfloat mean1, jfloat mean2,
   jfloat std0,  jfloat std1,  jfloat std2)
{
    fprintf(stderr, "[JNI DEBUG] preprocessToCHWNative called: outW=%d, outH=%d\n", outW, outH);

    if (!encodedImage) {
        fprintf(stderr, "[JNI ERROR] encodedImage is null\n");
        return nullptr;
    }

    auto* in_ptr = (unsigned char*) env->GetDirectBufferAddress(encodedImage);
    jlong in_len = env->GetDirectBufferCapacity(encodedImage);
    if (!in_ptr || in_len <= 0) {
        fprintf(stderr, "[JNI ERROR] Invalid buffer: ptr=%p, len=%ld\n", in_ptr, in_len);
        return nullptr;
    }

    fprintf(stderr, "[JNI DEBUG] Buffer address=%p, length=%ld\n", in_ptr, in_len);

    // 加载图像
    int w, h, c;
    unsigned char* hwc = stbi_load_from_memory(in_ptr, (int)in_len, &w, &h, &c, 3);
    if (!hwc) {
        fprintf(stderr, "[JNI ERROR] stbi_load_from_memory failed\n");
        return nullptr;
    }
    fprintf(stderr, "[JNI INFO] Loaded image %dx%d channels=%d\n", w, h, c);

    // Resize: 短边缩放到 256
    const int short_target = 256;
    double scale = (w < h) ? (double)short_target / w : (double)short_target / h;
    int rw = (int)std::round(w * scale);
    int rh = (int)std::round(h * scale);

    fprintf(stderr, "[JNI INFO] Resizing to %dx%d (scale=%.4f)\n", rw, rh, scale);

    unsigned char* resized256 = (unsigned char*) std::malloc((size_t)rw * rh * 3);
    if (!resized256) {
        fprintf(stderr, "[JNI ERROR] Failed to allocate resized buffer\n");
        stbi_image_free(hwc);
        return nullptr;
    }

    if (!stbir_resize_uint8_linear(
            hwc, w, h, w * 3,
            resized256, rw, rh, rw * 3,
            STBIR_RGB))
    {
        fprintf(stderr, "[JNI ERROR] stbir_resize_uint8_linear failed\n");
        std::free(resized256);
        stbi_image_free(hwc);
        return nullptr;
    }
    stbi_image_free(hwc);
    fprintf(stderr, "[JNI INFO] Resize completed\n");

    // Center crop
    unsigned char* cropped = (unsigned char*) std::malloc((size_t)outW * outH * 3);
    if (!cropped) {
        fprintf(stderr, "[JNI ERROR] Failed to allocate crop buffer\n");
        std::free(resized256);
        return nullptr;
    }

    center_crop_rgb_u8(resized256, rw, rh, cropped, outW, outH);
    std::free(resized256);
    fprintf(stderr, "[JNI INFO] Center crop to %dx%d completed\n", outW, outH);

    // 转换为 CHW 格式并归一化
    size_t N = (size_t)outW * outH * 3;
    float* chw = (float*) std::malloc(N * sizeof(float));
    if (!chw) {
        fprintf(stderr, "[JNI ERROR] Failed to allocate CHW buffer\n");
        std::free(cropped);
        return nullptr;
    }

    const float mean[3] = {mean0, mean1, mean2};
    const float stdv[3] = {std0, std1, std2};
    int hw = outW * outH;

    fprintf(stderr, "[JNI INFO] Converting to CHW with normalization (mean=[%.3f,%.3f,%.3f], std=[%.3f,%.3f,%.3f])\n",
            mean0, mean1, mean2, std0, std1, std2);

    for (int y = 0; y < outH; ++y) {
        for (int x = 0; x < outW; ++x) {
            int i = (y * outW + x) * 3;
            float r = (cropped[i]     / 255.0f - mean[0]) / (stdv[0] == 0 ? 1.0f : stdv[0]);
            float g = (cropped[i + 1] / 255.0f - mean[1]) / (stdv[1] == 0 ? 1.0f : stdv[1]);
            float b = (cropped[i + 2] / 255.0f - mean[2]) / (stdv[2] == 0 ? 1.0f : stdv[2]);

            int idx = y * outW + x;
            chw[idx]          = r;  // R channel
            chw[idx + hw]     = g;  // G channel
            chw[idx + 2 * hw] = b;  // B channel
        }
    }
    std::free(cropped);

    // 调试输出：打印前 10 个值
    fprintf(stderr, "[JNI DEBUG] First 10 values (R channel): ");
    for (int i = 0; i < 10 && i < hw; ++i) {
        fprintf(stderr, "%.4f ", chw[i]);
    }
    fprintf(stderr, "\n");

    // 验证数据有效性
    bool has_nan = false;
    bool has_inf = false;
    for (size_t i = 0; i < N; ++i) {
        if (std::isnan(chw[i])) has_nan = true;
        if (std::isinf(chw[i])) has_inf = true;
    }
    if (has_nan) fprintf(stderr, "[JNI WARNING] Data contains NaN!\n");
    if (has_inf) fprintf(stderr, "[JNI WARNING] Data contains Inf!\n");

    // 创建 Java float 数组
    jfloatArray result = env->NewFloatArray((jsize)N);
    if (result == nullptr) {
        fprintf(stderr, "[JNI ERROR] Failed to create Java float array\n");
        std::free(chw);
        return nullptr;
    }

    // 将数据复制到 Java 数组
    env->SetFloatArrayRegion(result, 0, (jsize)N, chw);

    // 检查 JNI 异常
    if (env->ExceptionCheck()) {
        fprintf(stderr, "[JNI ERROR] Exception occurred while setting float array\n");
        env->ExceptionDescribe();
        std::free(chw);
        return nullptr;
    }

    // 释放 C++ 内存
    std::free(chw);

    fprintf(stderr, "[JNI INFO] Successfully returned float array of size %zu\n", N);
    return result;
}