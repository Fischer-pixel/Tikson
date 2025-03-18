package com.example.nvbar;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.example.nvbar.ui.dashboard.DashboardFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.nvbar.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import cn.hzw.doodle.DoodleColor;
import cn.hzw.doodle.DoodleOnTouchGestureListener;
import cn.hzw.doodle.DoodleShape;
import cn.hzw.doodle.DoodleTouchDetector;
import cn.hzw.doodle.DoodleView;
import cn.hzw.doodle.IDoodleListener;
import cn.hzw.doodle.core.IDoodle;
import cn.hzw.doodle.core.IDoodleItem;
import cn.hzw.doodle.core.IDoodlePen;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("detect");
    }
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final int OPEN_GALLERY_REQUEST_CODE = 1;
    private static final int TAKE_PHOTO_REQUEST_CODE = 2;
    public ImageView mImg;
    private Bitmap yourSelectedImage, saveBitmap = null;
    public DoodleView doodleView = null;
    private ViewGroup container;
    private SeekBar seekBar;
    //被观察者：数据源持有该引用
    private OnDataChangeListener mDataChangeListener;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        View decorView = getWindow().getDecorView();  // 关闭顶部状态栏
//        // Hide the status bar.
//        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        //ActionBar actionBar = getActionBar();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.hide();
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        container = (ViewGroup) findViewById(R.id.doodle_container);
        mImg = findViewById(R.id.img);
        Button buttonImage = (Button) findViewById(R.id.btn_open_gallery);
        Button saveButton = (Button) findViewById(R.id.btn_save);
        Button undoButton = (Button) findViewById(R.id.btn_undo);

        buttonImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                applyPermission();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (saveBitmap == null)
                    return;
                saveImage(saveBitmap);
            }
        });

        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (doodleView == null)
                    return;
                doodleView.undo();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (doodleView == null)
                    return;
                // 根据progress的值调整图片的大小
                doodleView.setSize(progress * doodleView.getUnitSize());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


    }
    //这里接收的参数是任意实现了dataChangeListener接口的类的对象
    //目的是利用匿名内部类创建实现接口的对象来实现OnDataChangeListener方法
    public void setOnDataChangeListener(OnDataChangeListener dataChangeListener) {
        mDataChangeListener = dataChangeListener;
    }

    public interface OnDataChangeListener {
        void onDataChange(Bitmap data);
    }
    private void applyPermission() {
        //检测权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则申请需要的权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
        else {
            // 已经申请了权限
            openGallery();
        }
    }

    /**
     * 用户选择是否开启权限操作后的回调；TODO 同意/拒绝
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户同样授权
                openGallery();
            }else {
                // 用户拒绝授权
                Toast.makeText(this, "你拒绝使用存储权限！", Toast.LENGTH_SHORT).show();
                Log.d("HL", "你拒绝使用存储权限！");
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI , "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            try
            {
                if (requestCode == OPEN_GALLERY_REQUEST_CODE) {
                    Bitmap bitmap = decodeUri(selectedImage);

                    if (yourSelectedImage != null){
                        container.removeView(doodleView);
                    }

                    doodleView = new DoodleView(MainActivity.this, bitmap, new IDoodleListener() {
                        @Override
                        public void onSaved(IDoodle doodle, Bitmap doodleBitmap, Runnable callback) {
                            Toast.makeText(MainActivity.this, "onSaved", Toast.LENGTH_SHORT).show();
                        }
                        /*
                        called when it is ready to doodle because the view has been measured. Now, you can set size, color, pen, shape, etc.
                        此时view已经测量完成，涂鸦前的准备工作已经完成，在这里可以设置大小、颜色、画笔、形状等。
                        */
                        @Override
                        public void onReady(IDoodle doodle) {
                            // doodle.setSize(10 * doodle.getUnitSize());  // 此处调节画笔的粗细
                            doodle.setSize(seekBar.getProgress() * doodle.getUnitSize());  // 此处调节画笔的粗细

                        }
                    });

                    // step 2
                    DoodleOnTouchGestureListener touchGestureListener = new DoodleOnTouchGestureListener(doodleView, null);
                    DoodleTouchDetector touchDetector = new DoodleTouchDetector(this, touchGestureListener);
                    doodleView.setDefaultTouchDetector(touchDetector);

                    // step 3
                    doodleView.setPen(new MosaicPen());
                    doodleView.setShape(DoodleShape.HAND_WRITE);  //DoodleShape.HAND_WRITE
                    //doodleView.setColor(getMosaicColor(20));
                    doodleView.setColor(new DoodleColor(Color.RED));
                    // step 4
                    container.addView(doodleView);

                    yourSelectedImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    if (mDataChangeListener == null){
                        return;
                    }
                    mDataChangeListener.onDataChange(yourSelectedImage);
//                    FragmentManager fragmentManager = getSupportFragmentManager();
//                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
//                    // 创建一个新的ExampleFragment2实例
//                    DashboardFragment exampleFragment2 = new DashboardFragment();
//                    // 创建一个Bundle对象，用于传递数据到新的片段
//                    Bundle bundle = new Bundle();
//                    // 向Bundle中添加字符串数据和整型数据
//                    bundle.putString("data", "我的数据3--Argument");
//                    bundle.putParcelable("bitmap",yourSelectedImage);
//                    // bundle.putInt("int_data", 10);
//                    // 将Bundle对象设置为新的片段的参数
//                    exampleFragment2.setArguments(bundle);
//                    // 用新的片段替换布局中的片段容器，并提交此事务
//                    fragmentTransaction.replace(R.id.navigation_dashboard, exampleFragment2).commit();

                }
            }
            catch (FileNotFoundException e)
            {
                Log.e("MainActivity", "FileNotFoundException");
                return;
            }
        }
    }

    /*
     Though setting a new pen here is not necessary, the design-based specification should do this.
     虽然这里设置新的画笔不是必要的，但是基于设计的规范应该这样做。马赛克画笔在概念上不同于其他画笔，
     */
    private static class MosaicPen implements IDoodlePen {
        @Override
        public void config(IDoodleItem doodleItem, Paint paint) {
        }

        @Override
        public void drawHelpers(Canvas canvas, IDoodle doodle) {
        }

        @Override
        public IDoodlePen copy() {
            return this;
        }
    }

    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException
    {
        // Decode image size

        // Decode with inSampleSize
        BitmapFactory.Options o = new BitmapFactory.Options();
        //o.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

        // Rotate according to EXIF
        int rotate = 0;
        try
        {
            ExifInterface exif = new ExifInterface(Objects.requireNonNull(getContentResolver().openInputStream(selectedImage)));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        }
        catch (IOException e)
        {
            Log.e("MainActivity", "ExifInterface IOException");
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public void setReceive(Bitmap data) {
        saveBitmap = data;
    }
    public void saveImage(Bitmap save_bitmap) {
        //开始一个新的进程执行保存图片的操作
        Uri insertUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        //使用use可以自动关闭流
        try {
            assert insertUri != null;
            OutputStream outputStream = getContentResolver().openOutputStream(insertUri, "rw");
            assert outputStream != null;
            if (save_bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                Toast.makeText(this, "图片成功保存至相册！", Toast.LENGTH_SHORT).show();
                Log.e("保存成功", "success");
            } else {
                Toast.makeText(this, "图片保存失败！", Toast.LENGTH_SHORT).show();
                Log.e("保存失败", "fail");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}