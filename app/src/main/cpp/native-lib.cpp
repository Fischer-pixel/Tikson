#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>

// ncnn
#include "layer.h"
#include "net.h"
#include "benchmark.h"
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
using namespace cv;
//
//
//extern "C" {
//#define ASSERT(status, ret)     if (!(status)) { return ret; }
//#define ASSERT_FALSE(status)    ASSERT(status, false)
//bool BitmapToMatrix(JNIEnv * env, jobject obj_bitmap, cv::Mat & matrix) {
//    void * bitmapPixels;                                            // Save picture pixel data
//    AndroidBitmapInfo bitmapInfo;                                   // Save picture parameters
//
//    ASSERT_FALSE( AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);        // Get picture parameters
//    ASSERT_FALSE( bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888
//                  || bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565 );          // Only ARGB? 8888 and RGB? 565 are supported
//    ASSERT_FALSE( AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0 );  // Get picture pixels (lock memory block)
//    ASSERT_FALSE( bitmapPixels );
//
//    if (bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
//        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);    // Establish temporary mat
//        tmp.copyTo(matrix);                                                         // Copy to target matrix
//    } else {
//        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC2, bitmapPixels);
//        cv::cvtColor(tmp, matrix, cv::COLOR_BGR5652RGB);
//    }
//
//    //convert RGB to BGR
//    //cv::cvtColor(matrix,matrix,cv::COLOR_RGB2BGR);
//
//    AndroidBitmap_unlockPixels(env, obj_bitmap);            // Unlock
//    return true;
//}
//
//
////bool MatrixToBitmap(JNIEnv * env, cv::Mat & matrix, jobject obj_bitmap) {
////    void * bitmapPixels;                                            // Save picture pixel data
////    AndroidBitmapInfo bitmapInfo;                                   // Save picture parameters
////
////    ASSERT_FALSE( AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);        // Get picture parameters
////    ASSERT_FALSE( bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888
////                  || bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565 );          // Only ARGB? 8888 and RGB? 565 are supported
////    ASSERT_FALSE( matrix.dims == 2
////                  && bitmapInfo.height == (uint32_t)matrix.rows
////                  && bitmapInfo.width == (uint32_t)matrix.cols );                   // It must be a 2-dimensional matrix with the same length and width
////    ASSERT_FALSE( matrix.type() == CV_8UC1 || matrix.type() == CV_8UC3 || matrix.type() == CV_8UC4 );
////    ASSERT_FALSE( AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0 );  // Get picture pixels (lock memory block)
////    ASSERT_FALSE( bitmapPixels );
////
////    if (bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
////        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);
////        switch (matrix.type()) {
////            case CV_8UC1:   cv::cvtColor(matrix, tmp, cv::COLOR_GRAY2RGBA);     break;
////            case CV_8UC3:   cv::cvtColor(matrix, tmp, cv::COLOR_RGB2RGBA);      break;
////            case CV_8UC4:   matrix.copyTo(tmp);                                 break;
////            default:        AndroidBitmap_unlockPixels(env, obj_bitmap);        return false;
////        }
////    } else {
////        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC2, bitmapPixels);
////        switch (matrix.type()) {
////            case CV_8UC1:   cv::cvtColor(matrix, tmp, cv::COLOR_GRAY2BGR565);   break;
////            case CV_8UC3:   cv::cvtColor(matrix, tmp, cv::COLOR_RGB2BGR565);    break;
////            case CV_8UC4:   cv::cvtColor(matrix, tmp, cv::COLOR_RGBA2BGR565);   break;
////            default:        AndroidBitmap_unlockPixels(env, obj_bitmap);        return false;
////        }
////    }
////    AndroidBitmap_unlockPixels(env, obj_bitmap);                // Unlock
////    return true;
////}
//void MatrixToBitmap(JNIEnv* env, cv::Mat &mat, jobject bitmap) {
//    void* bitmapPixels;
//    AndroidBitmapInfo info;
//
//    // 获取 Bitmap 信息
//    AndroidBitmap_getInfo(env, bitmap, &info);
//
//    // 锁定 Bitmap，以便将数据写入其中
//    AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels);
//
//    // 假设 mat 的类型与 bitmap 的格式匹配，例如都是 CV_8UC4 (ARGB_8888)
//    // 如果类型不匹配，需要先转换 mat 的类型
//    if (mat.type() == CV_8UC4) {
//        memcpy(bitmapPixels, mat.data, mat.total() * mat.elemSize());
//    } else {
//        // 其他类型的转换可以使用 cv::cvtColor 来实现
//        cv::Mat temp;
//        cv::cvtColor(mat, temp, cv::COLOR_RGB2BGRA);
//        memcpy(bitmapPixels, temp.data, temp.total() * temp.elemSize());
//    }
//
//    // 解锁 Bitmap
//    AndroidBitmap_unlockPixels(env, bitmap);
//    return;
//}
//
//jobject mat_to_bitmap(JNIEnv *env, Mat &src, bool needPremultiplyAlpha, jobject bitmap_config) {
//    jclass java_bitmap_class = (jclass) env->FindClass("android/graphics/Bitmap");
//    jmethodID mid = env->GetStaticMethodID(java_bitmap_class,
//                                           "createBitmap",
//                                           "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
//
//    jobject bitmap = env->CallStaticObjectMethod(java_bitmap_class,
//                                                 mid, src.size().width, src.size().height,
//                                                 bitmap_config);
//    AndroidBitmapInfo info;
//    void *pixels = 0;
//
//    CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
//    CV_Assert(src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4);
//    CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
//    CV_Assert(pixels);
//    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
//        Mat tmp(info.height, info.width, CV_8UC4, pixels);
//        if (src.type() == CV_8UC1) {
//            cvtColor(src, tmp, COLOR_GRAY2RGBA);
//        } else if (src.type() == CV_8UC3) {
//            cvtColor(src, tmp, COLOR_RGB2RGBA);
//        } else if (src.type() == CV_8UC4) {
//            if (needPremultiplyAlpha) {
//                cvtColor(src, tmp, COLOR_RGBA2mRGBA);
//            } else {
//                src.copyTo(tmp);
//            }
//        }
//    } else {
//        // info.format == ANDROID_BITMAP_FORMAT_RGB_565
//        Mat tmp(info.height, info.width, CV_8UC2, pixels);
//        if (src.type() == CV_8UC1) {
//            cvtColor(src, tmp, COLOR_GRAY2BGR565);
//        } else if (src.type() == CV_8UC3) {
//            cvtColor(src, tmp, COLOR_RGB2BGR565);
//        } else if (src.type() == CV_8UC4) {
//            cvtColor(src, tmp, COLOR_RGBA2BGR565);
//        }
//    }
//    AndroidBitmap_unlockPixels(env, bitmap);
//    return bitmap;
//}
//
//
//JNIEXPORT void JNICALL
//Java_com_example_realesrgan_MainActivity_JniBitmapExec(JNIEnv * env, jobject /* this */,
//                                                          jobject obj_bitmap, jobject obj_bitmapOut)
//{
//    cv::Mat matBitmap;
//    bool ret = BitmapToMatrix(env, obj_bitmap, matBitmap);          // Bitmap to cv::Mat
//    if (!ret) {
//        return;
//    }
//
//    // opencv processing of mat
//    cv::rectangle(matBitmap, cv::Rect(cv::Point(100, 100), cv::Size(100, 100)),
//                  cv::Scalar(255, 255, 255), -1);
//
//    char text[50];
//    sprintf(text, "%s", "FPS");
//
//    cv::putText(matBitmap, text, cv::Point(15, 30),
//                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));
//
//    MatrixToBitmap(env, matBitmap, obj_bitmapOut);       // Bitmap to cv::Mat
//    sprintf(text, "%s", "FPS");
////    if (!ret) {
////        return;
////    }
//}
//
//JNIEXPORT jobject JNICALL
//Java_com_example_realesrgan_MainActivity_filmEffect(JNIEnv *env, jobject thiz,
//                                                       jobject origin_bitmap) {
//    // 底片效果 利用 opencv 的 Mat
//    AndroidBitmapInfo bitmapInfo;
//    int ret = AndroidBitmap_getInfo(env, origin_bitmap, &bitmapInfo);
//    if (ret != 0) {
//        return NULL;
//    }
//    void *pixel;
//    AndroidBitmap_lockPixels(env, origin_bitmap, &pixel);
//
//    Mat src;
//    BitmapToMatrix(env, origin_bitmap, src);
//
//
//    // 区域截取
////    Mat srcROI = src(Rect(0, 0, bitmapInfo.width, bitmapInfo.height));
//
//    int rows = src.rows;
//    int cols = src.cols;
//
//    for (int i = 0; i < rows; i++) {
//        for (int j = 0; j < cols; j++) {
//            // 获取像素
//            int b = src.at<Vec3b>(i, j)[0];
//            int g = src.at<Vec3b>(i, j)[1];
//            int r = src.at<Vec3b>(i, j)[2];
//
//            // 修改像素
//            src.at<Vec3b>(i, j)[0] = 255 - b;
//            src.at<Vec3b>(i, j)[1] = 255 - g;
//            src.at<Vec3b>(i, j)[2] = 255 - r;
//        }
//    }
//    //get source bitmap's config
//    jclass java_bitmap_class = (jclass) env->FindClass("android/graphics/Bitmap");
//    jmethodID mid = env->GetMethodID(java_bitmap_class, "getConfig",
//                                     "()Landroid/graphics/Bitmap$Config;");
//    jobject bitmap_config = env->CallObjectMethod(origin_bitmap, mid);
//    jobject _bitmap = mat_to_bitmap(env, src, false, bitmap_config);
//
//    AndroidBitmap_unlockPixels(env, origin_bitmap);
//
//    return _bitmap;
//
//}
//
//}
