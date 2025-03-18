package com.example.nvbar;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class DetectNcnn {
    public native boolean GanInit(AssetManager mgr, int id);
    public native boolean U2netInit(AssetManager mgr, int id);

    public native boolean GanInfer(Bitmap bitmap, Bitmap bitmap1);

    public native boolean U2netInfer(Bitmap bitmap, Bitmap bitmap1);

    static {
        System.loadLibrary("detect");
    }
}
