#include <jni.h>
#include <cstdlib>
#include <cmath>
#include <cstring>

#define STB_IMAGE_IMPLEMENTATION
#include "third_party/stb_image.h"

#define STB_IMAGE_RESIZE2_IMPLEMENTATION
#include "third_party/stb_image_resize2.h"

#include "org_estech_api_jni_NativeImageOps.h"

static jobject wrap_as_direct(JNIEnv* env, float* data, size_t bytes) {
    return env->NewDirectByteBuffer((void*)data, (jlong)bytes);
}

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

JNIEXPORT jobject JNICALL Java_org_estech_api_jni_NativeImageOps_preprocessToCHWNative
  (JNIEnv* env, jclass,
   jobject encodedImage, jint outW, jint outH,
   jfloat mean0, jfloat mean1, jfloat mean2,
   jfloat std0,  jfloat std1,  jfloat std2)
{
    if (!encodedImage) {
        return nullptr;
    }

    auto* in_ptr = (unsigned char*) env->GetDirectBufferAddress(encodedImage);
    jlong in_len = env->GetDirectBufferCapacity(encodedImage);

    if (!in_ptr || in_len <= 0) {
        return nullptr;
    }

    int w, h, c;
    unsigned char* hwc = stbi_load_from_memory(in_ptr, (int)in_len, &w, &h, &c, 3);
    if (!hwc) {
        return nullptr;
    }

    const int short_target = 256;
    double scale = (w < h) ? (double)short_target / w : (double)short_target / h;
    int rw = (int)std::round(w * scale);
    int rh = (int)std::round(h * scale);

    unsigned char* resized256 = (unsigned char*) std::malloc((size_t)rw * rh * 3);
    if (!resized256) {
        stbi_image_free(hwc);
        return nullptr;
    }

    int src_stride = w * 3 * sizeof(unsigned char);
    int dst_stride = rw * 3 * sizeof(unsigned char);

    if (!stbir_resize_uint8_linear(
            hwc, w, h, src_stride,
            resized256, rw, rh, dst_stride,
            STBIR_RGB))
    {
        std::free(resized256);
        stbi_image_free(hwc);
        return nullptr;
    }

    stbi_image_free(hwc);

    unsigned char* cropped = (unsigned char*) std::malloc((size_t)outW * outH * 3);
    if (!cropped) {
        std::free(resized256);
        return nullptr;
    }

    center_crop_rgb_u8(resized256, rw, rh, cropped, outW, outH);
    std::free(resized256);

    size_t N = (size_t)outW * outH * 3;
    float* chw = (float*) std::malloc(N * sizeof(float));
    if (!chw) {
        std::free(cropped);
        return nullptr;
    }

    const float mean[3] = {mean0, mean1, mean2};
    const float stdv[3] = {std0, std1, std2};
    int hw = outW * outH;

    for (int y = 0; y < outH; ++y) {
        for (int x = 0; x < outW; ++x) {
            int i = (y * outW + x) * 3;
            float r = (cropped[i    ] / 255.0f - mean[0]) / stdv[0];
            float g = (cropped[i + 1] / 255.0f - mean[1]) / stdv[1];
            float b = (cropped[i + 2] / 255.0f - mean[2]) / stdv[2];
            int idx = y * outW + x;
            chw[idx]        = r;
            chw[idx + hw]   = g;
            chw[idx + 2*hw] = b;
        }
    }
    std::free(cropped);

    jobject buffer = wrap_as_direct(env, chw, N * sizeof(float));

    if (buffer == nullptr) {
        std::free(chw);
        return nullptr;
    }

    return buffer;
}

JNIEXPORT void JNICALL Java_org_estech_api_jni_NativeImageOps_freeBufferNative
  (JNIEnv* env, jclass, jobject buffer)
{
    if (!buffer) return;
    void* p = env->GetDirectBufferAddress(buffer);
    if (p) std::free(p);
}