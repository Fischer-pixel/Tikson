package com.example.nvbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class ImageUtils {
    //宽 高 类型 to 类 宽 高
    public static float[] whc2cwh(float[] src) {
        float[] chw = new float[src.length];
        int j = 0;
        for (int ch = 0; ch < 3; ++ch) {
            for (int i = ch; i < src.length; i += 3) {
                chw[j] = src[i];
                j++;
            }
        }
        return chw;
    }

    public static float[] preprocessImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];  // 创建一个数组存储bitmap的8位整数值，最后一个量存储三个通道
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] floatValues = new float[width * height * 3];


        for (int i = 0; i < pixels.length; i++) {
            int pixelValue = pixels[i];
            int r = Color.red(pixelValue);
            int g = Color.green(pixelValue);
            int b = Color.blue(pixelValue);
//            floatValues[i * 3] = (float) ((r / 255.0f) - 0.485f) / 0.229f;  // 8位整形归一化再标准化，与原始u2net项目保持一致
//            floatValues[i * 3 + 1] = (float) ((g / 255.0f) - -0.456f) / 0.224f;
//            floatValues[i * 3 + 2] =(float) ((b / 255.0f) - 0.406f) / 0.225f;

            floatValues[i * 3] = (float) (r / 255.0f);  // 8位整形归一化，与原始lama项目保持一致
            floatValues[i * 3 + 1] = (float) (g / 255.0f);
            floatValues[i * 3 + 2] =(float) (b / 255.0f);
        }

        return whc2cwh(floatValues);
    }

    public static int[] generateMask2(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];  // 创建一个数组存储bitmap的8位整数值，最后一个量存储三个通道
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] maskValues = new int[width * height];

        for (int i = 0; i < pixels.length; i++) {
            int pixelValue = pixels[i];
            int r = Color.red(pixelValue);
            int g = Color.green(pixelValue);
            int b = Color.blue(pixelValue);
            if(r==0){
                maskValues[i] = 0;
            }
            else{
                maskValues[i] = 1;
            }

        }

        return maskValues;
    }
    public static Bitmap checkLamaRatio(Bitmap bitmap){
        // Lama模型对输入图像的分辨率有要求，其宽高分辨率必须为8的整数倍且最短边不能低于256像素
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minSide = Math.min(width, height);//找出最短边

        float widthScaleRatio = 1.f;
        float heightScaleRatio = 1.f;
        if (minSide < 256) {
            heightScaleRatio = widthScaleRatio = (float) 256. / minSide; //java的除法为自动向下取整，因此需要使用Math.floor进行四舍五入
        }
        if (minSide > 720) {
            heightScaleRatio = widthScaleRatio = (float) 640. / minSide; //java的除法为自动向下取整，因此需要使用Math.floor进行四舍五入
        }
        height = (int) ((int) height * heightScaleRatio);  //先将原图缩放为256分辨率以上
        width = (int) ((int) width * widthScaleRatio);

        //当分辨率不能被8整除
        widthScaleRatio = (float) Math.floor((float) (width / 8));
        heightScaleRatio = (float) Math.floor((float) height / 8);

        int newHeight = (int) ((int) 8 * heightScaleRatio);
        int newWidth = (int) ((int) 8 * widthScaleRatio);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);  // 是否使用双边滤波，使用后图片更平滑
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
