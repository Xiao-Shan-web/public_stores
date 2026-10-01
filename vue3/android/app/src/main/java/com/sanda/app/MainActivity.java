package com.sanda.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.BridgeActivity;

/**
 * 应用主入口
 * 1. 注册 SimCard 插件（供前端 Capacitor.Plugins.SimCard 调用）
 * 2. 启动时尝试预读 SIM 卡号并缓存到 SharedPreferences（授权后下次可直接命中）
 */
public class MainActivity extends BridgeActivity {

    private static final String PREF_NAME = "sanda";
    private static final String KEY_SIM_PHONE = "sim_phone";
    private static final int REQ_PHONE_STATE = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 注册原生插件（须在 super.onCreate 之前）
        registerPlugin(SimCardPlugin.class);

        super.onCreate(savedInstanceState);

        // 预读 SIM 卡号并缓存
        cacheSimPhoneIfPermitted();
    }

    /**
     * 若 READ_PHONE_STATE 已授权则读取 SIM 号并缓存；
     * 未授权则请求权限（异步回调，授权后下次启动可命中缓存）
     */
    private void cacheSimPhoneIfPermitted() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PHONE_STATE);
            return;
        }
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String phone = tm.getLine1Number();
                if (phone != null && !phone.isEmpty()) {
                    SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    sp.edit().putString(KEY_SIM_PHONE, phone).apply();
                }
            }
        } catch (Exception e) {
            // 读取失败忽略，前端将回退到后端接口
        }
    }
}
