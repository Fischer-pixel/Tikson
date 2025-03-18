package com.example.nvbar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Welcome extends AppCompatActivity {
    public String welcome;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

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

        SharedPreferences sp=getSharedPreferences("appDetails", Context.MODE_PRIVATE);
        welcome = sp.getString("welcome",null);
        if (welcome==null){
            //显示第一种欢迎界面的代码 ……（此处省略）
            sp.edit().putString("welcome","1").apply();
        }
        else{
            //plan1 timer
//            new Timer().schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    startActivity(new Intent(getApplicationContext(), home.class));
//                    finish();
//                }
//            }, 2000);
            //plan2 handler

        }
        new Handler(new Handler.Callback() {
            // 处理接收到消息的方法
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                finish();
                return false;
            }
        }).sendEmptyMessageDelayed(0,2500);
    }
}
