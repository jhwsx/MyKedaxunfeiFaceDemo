package com.wan.face;

import android.app.Application;

import com.iflytek.cloud.SpeechUtility;

/**
 * Created by wzc on 2017/12/11.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 在程序入口处传入appid，初始化SDK
        SpeechUtility.createUtility(this, "appid=" + getString(R.string.app_id));
    }
}
