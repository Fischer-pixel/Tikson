#include <jni.h>
#include <string>
#include "net.h"
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "cpu.h"
#include "benchmark.h"
#include "vector"
#include "unordered_map"
#include "detect.hpp"
#include <string>
#include <locale>
#include <codecvt>
#include <sstream>
using namespace std;


static float clamp(
        float val,
        float min = 0.f,
        float max = 1280.f
)
{
    return val > min ? (val < max ? val : max) : min;
}

//https://www.jianshu.com/p/9c0ba013aa9c
static std::string UTF16StringToUTF8String(const char16_t* chars, size_t len)
{
    std::u16string u16_string(chars, len);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}
            .to_bytes(u16_string);
}
void transpose(const ncnn::Mat& in, ncnn::Mat& out)
{
    ncnn::Option opt;
    opt.num_threads = 2;
    opt.use_fp16_storage = false;
    opt.use_packing_layout = true;

    ncnn::Layer* op = ncnn::create_layer("Permute");

    // set param
    ncnn::ParamDict pd;
    pd.set(0, 1);// order_type

    op->load_param(pd);

    op->create_pipeline(opt);

    ncnn::Mat in_packed = in;
    {
        // resolve dst_elempack
        int dims = in.dims;
        int elemcount = 0;
        if (dims == 1) elemcount = in.elempack * in.w;
        if (dims == 2) elemcount = in.elempack * in.h;
        if (dims == 3) elemcount = in.elempack * in.c;

        int dst_elempack = 1;
        if (op->support_packing)
        {
            if (elemcount % 8 == 0 && (ncnn::cpu_support_x86_avx2() || ncnn::cpu_support_x86_avx()))
                dst_elempack = 8;
            else if (elemcount % 4 == 0)
                dst_elempack = 4;
        }

        if (in.elempack != dst_elempack)
        {
            convert_packing(in, in_packed, dst_elempack, opt);
        }
    }

    // forward
    op->forward(in_packed, out, opt);

    op->destroy_pipeline(opt);

    delete op;
}

extern "C" {
static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

static ncnn::Net gan_model;
static ncnn::Net u2net_model;
const int target_size = 320;

#define ASSERT(status, ret)     if (!(status)) { return ret; }
#define ASSERT_FALSE(status)    ASSERT(status, false)
bool BitmapToMatrix(JNIEnv * env, jobject obj_bitmap, cv::Mat & matrix) {
    void * bitmapPixels;                                            // Save picture pixel data
    AndroidBitmapInfo bitmapInfo;                                   // Save picture parameters

    ASSERT_FALSE( AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);        // Get picture parameters
    ASSERT_FALSE( bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888
                  || bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565);          // Only ARGB? 8888 and RGB? 565 are supported
    ASSERT_FALSE( AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0 );  // Get picture pixels (lock memory block)
    ASSERT_FALSE( bitmapPixels );

    if (bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);    // Establish temporary mat
        tmp.copyTo(matrix);                                                         // Copy to target matrix
    } else {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC2, bitmapPixels);
        cv::cvtColor(tmp, matrix, cv::COLOR_BGR5652RGB);
    }

    //convert RGB to BGR
    //cv::cvtColor(matrix,matrix,cv::COLOR_RGB2BGR);

    AndroidBitmap_unlockPixels(env, obj_bitmap);            // Unlock
    return true;
}

void MatrixToBitmap(JNIEnv* env, cv::Mat &mat, jobject bitmap) {
    void* bitmapPixels;
    AndroidBitmapInfo info;

    // 获取 Bitmap 信息
    AndroidBitmap_getInfo(env, bitmap, &info);

    // 锁定 Bitmap，以便将数据写入其中
    AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels);

    // 假设 mat 的类型与 bitmap 的格式匹配，例如都是 CV_8UC4 (ARGB_8888)
    // 如果类型不匹配，需要先转换 mat 的类型
    if (mat.type() == CV_8UC4) {
        memcpy(bitmapPixels, mat.data, mat.total() * mat.elemSize());
    } else {
        // 其他类型的转换可以使用 cv::cvtColor 来实现
        cv::Mat temp;
        cv::cvtColor(mat, temp, cv::COLOR_RGB2BGRA);
        memcpy(bitmapPixels, temp.data, temp.total() * temp.elemSize());
    }

    // 解锁 Bitmap
    AndroidBitmap_unlockPixels(env, bitmap);
    return;
}

jobject mat_to_bitmap(JNIEnv *env, cv::Mat &src, bool needPremultiplyAlpha, jobject bitmap_config) {
    jclass java_bitmap_class = (jclass) env->FindClass("android/graphics/Bitmap");
    jmethodID mid = env->GetStaticMethodID(java_bitmap_class,
                                           "createBitmap",
                                           "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(java_bitmap_class,
                                                 mid, src.size().width, src.size().height,
                                                 bitmap_config);
    AndroidBitmapInfo info;
    void *pixels = 0;

    CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
    CV_Assert(src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4);
    CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
    CV_Assert(pixels);
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        if (src.type() == CV_8UC1) {
            cvtColor(src, tmp, cv::COLOR_GRAY2RGBA);
        } else if (src.type() == CV_8UC3) {
            cvtColor(src, tmp, cv::COLOR_RGB2RGBA);
        } else if (src.type() == CV_8UC4) {
            if (needPremultiplyAlpha) {
                cvtColor(src, tmp, cv::COLOR_RGBA2mRGBA);
            } else {
                src.copyTo(tmp);
            }
        }
    } else {
        // info.format == ANDROID_BITMAP_FORMAT_RGB_565
        cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
        if (src.type() == CV_8UC1) {
            cvtColor(src, tmp, cv::COLOR_GRAY2BGR565);
        } else if (src.type() == CV_8UC3) {
            cvtColor(src, tmp, cv::COLOR_RGB2BGR565);
        } else if (src.type() == CV_8UC4) {
            cvtColor(src, tmp, cv::COLOR_RGBA2BGR565);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}


JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "RealESRGANNcnn", "JNI_OnLoad");

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "RealESRGANNcnn", "JNI_OnUnload");

    ncnn::destroy_gpu_instance();
}

//jstring -> std::string
std::string JavaStringToString(JNIEnv* env, jstring str)
{
    if (env == nullptr || str == nullptr)
    {
        return "";
    }
    const jchar* chars = env->GetStringChars(str, NULL);
    if (chars == nullptr)
    {
        return "";
    }
    std::string u8_string = UTF16StringToUTF8String(
            reinterpret_cast<const char16_t*>(chars), env->GetStringLength(str));
    env->ReleaseStringChars(str, chars);
    return u8_string;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nvbar_DetectNcnn_GanInit(JNIEnv *env, jobject thiz, jobject assetManager, jint model_id) {
//    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
//    opt.use_int8_inference= true;
    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    gan_model.opt = opt;

    const char* modeltypes[] =
            {
                    "realesr-general-x4",
                    "realesrgan-x4",
                    "realesrgan-anime-x4"
            };

    char parampath[256];
    char modelpath[256];
    const char* modeltype = modeltypes[(int)model_id];
    sprintf(parampath, "%s.param", modeltype);
    sprintf(modelpath, "%s.bin", modeltype);

    // init param
    {
        int ret = gan_model.load_param(mgr,parampath);
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Detect_Ncnn", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = gan_model.load_model(mgr,modelpath);
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Detect_Ncnn", "load_bin failed");
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nvbar_DetectNcnn_U2netInit(JNIEnv *env, jobject thiz, jobject assetManager, jint model_id) {
//    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
//    opt.use_int8_inference= true;
    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    u2net_model.opt = opt;
    const char* modeltypes[] =
            {
                    "u2net-opt",
                    "u2netp-opt",
            };

    char parampath[256];
    char modelpath[256];
    const char* modeltype = modeltypes[(int)model_id];
    sprintf(parampath, "%s.param", modeltype);
    sprintf(modelpath, "%s.bin", modeltype);

    // init param
    {
        int ret = u2net_model.load_param(mgr,parampath);
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Detect_Ncnn", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = u2net_model.load_model(mgr,modelpath);
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Detect_Ncnn", "load_bin failed");
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nvbar_DetectNcnn_GanInfer(JNIEnv *env, jobject thiz, jobject bitmap, jobject bitmapOut){
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    int width = info.width;
    int height = info.height;

    // ncnn from bitmap
    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    float norm_vals[] ={ 1 / 255.f, 1 / 255.f, 1 / 255.f };
    in.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex = gan_model.create_extractor();
    if (ncnn::get_gpu_count() != 0)
        ex.set_vulkan_compute("true");

    ex.input("images", in);

    // output image
    ncnn::Mat out;
    {
        ex.extract("output0", out);  // 2048 x 2048 x 3
    }
    // w=3,h=880,d=1,c=880
    float norm_vals_post[] ={ 255.f, 255.f, 255.f };
    out.substract_mean_normalize(0, norm_vals_post);

    out.to_android_bitmap(env, bitmapOut, ncnn::Mat::PIXEL_RGB);
    // MatToBitmap2(env, opencv_mat, bitmap_out_, false);

    return JNI_TRUE;
}


JNIEXPORT jboolean JNICALL
Java_com_example_nvbar_DetectNcnn_U2netInfer(JNIEnv *env, jobject thiz, jobject bitmap, jobject bitmapOut){
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    int width = info.width;
    int height = info.height;
    float scale = 1.f;
    if (width > height)
    {
        scale = (float)target_size / width;
        width = target_size;
        height = height * scale;
    }
    else
    {
        scale = (float)target_size / height;
        height = target_size;
        width = width * scale;
    }

    // ncnn from bitmap
    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB,width,height);

    ncnn::Mat in_origin = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGBA);

    cv::Mat mat_origin(height, width, CV_8UC4);
    BitmapToMatrix(env, bitmap, mat_origin);
    //nativeBitmapToMat(env, bitmap, mat_origin);
    int c = mat_origin.channels();

    // pad to target_size rectangle
    int wpad = target_size - width;
    int hpad = target_size - height;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2,
                           ncnn::BORDER_CONSTANT, 114.f);

    float norm_vals1[] ={ 1 / 255.f, 1 / 255.f, 1 / 255.f };
    in_pad.substract_mean_normalize(0, norm_vals1);
//    const float mean_vals[3] = {0.485, 0.456, 0.406};
//    const float norm_vals[3] = {0.229, 0.224, 0.225};
//    in_pad.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = u2net_model.create_extractor();
    if (ncnn::get_gpu_count() != 0)
        ex.set_vulkan_compute("true");

    ex.input("images", in_pad);

    // output image
    ncnn::Mat out;
    {
        ex.extract("output0", out);  // 2048 x 2048 x 3
    }

    // w=3,h=880,d=1,c=880
    float norm_vals_post[] ={ 255.f, 255.f, 255.f };
    out.substract_mean_normalize(0, norm_vals_post);

//    cv::Mat scale_mask(in_pad.h, in_pad.w, CV_8UC1);
//    out.to_pixels(scale_mask.data, ncnn::Mat::PIXEL_GRAY);
//    std::vector<cv::Mat> mv = {scale_mask, scale_mask, scale_mask, scale_mask};
//    cv::merge(mv, scale_mask);
//    MatrixToBitmap(env, scale_mask, bitmapOut);  //这一段实现了全白的提取, for debug


    cv::Mat cv_mat(in_pad.h, in_pad.w, CV_8UC1);
    out.to_pixels(cv_mat.data, ncnn::Mat::PIXEL_GRAY);
    c = cv_mat.channels();

    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(3, 3));
    cv::morphologyEx(cv_mat, cv_mat, cv::MORPH_OPEN, kernel);

    cv::GaussianBlur(cv_mat, cv_mat, cv::Size(5, 5),
                     2, 2, cv::BORDER_DEFAULT);

    cv::Mat mask;
//    cv_mat.copyTo(mask);
    mask = cv_mat(cv::Rect(wpad / 2, hpad / 2, width, height));

    cv::resize(mask, mask, cv::Size(info.width, info.height), 0, 0,
               cv::INTER_LANCZOS4);
//    std::vector<cv::Mat> mv = {mask, mask, mask, mask};
//    cv::merge(mv, mask);

//    // 传统二值化，手动指定阈值
////    float thresholdValue = 127.f;
////    cv::threshold(scale_mask, binaryImage, thresholdValue, 255, cv::THRESH_BINARY);
//    int channels = mat_origin.channels();  // 4
    cv::Mat result = cv::Mat::zeros(cv::Size(info.width, info.height), CV_8UC4);
    mat_origin.copyTo(result, mask > 127.f);
    // cv::bitwise_and(mat_origin, scale_mask, result);

    MatrixToBitmap(env, result, bitmapOut);

    //printf("text");
    AndroidBitmap_unlockPixels(env, bitmap);

    return JNI_TRUE;
}


};
