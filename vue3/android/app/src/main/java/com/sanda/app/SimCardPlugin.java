package com.sanda.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * SIM 卡读取插件
 * 名称 "SimCard" 与前端 registerPlugin('SimCard') 对应
 *
 * 读取策略：
 *   1. 优先读取 SharedPreferences 中由 MainActivity 启动时缓存的号码
 *   2. 缓存为空且 READ_PHONE_STATE 已授权，则直接读取 TelephonyManager
 *   3. 均失败返回空字符串，前端回退到登录页由用户手动输入手机号
 */
@CapacitorPlugin(name = "SimCard")
public class SimCardPlugin extends Plugin {

    private static final String PREF_NAME = "sanda";
    private static final String KEY_SIM_PHONE = "sim_phone";

    @PluginMethod
    public void getPhoneNumber(PluginCall call) {
        JSObject ret = new JSObject();
        String phone = "";

        // 1. 优先从 SharedPreferences 读取（MainActivity 启动时已缓存）
        SharedPreferences sp = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cached = sp.getString(KEY_SIM_PHONE, "");

        boolean cachedEmpty = (cached == null || cached.isEmpty());

        // 2. 缓存为空且权限已授予，则直接读取 TelephonyManager
        if (cachedEmpty &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {
            try {
                TelephonyManager tm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String num = tm.getLine1Number();
                    if (num != null && !num.isEmpty()) {
                        phone = num;
                        // 缓存以便下次直接返回
                        sp.edit().putString(KEY_SIM_PHONE, num).apply();
                    }
                }
            } catch (Exception e) {
                // 读取失败：保持空，前端回退
            }
        } else if (!cachedEmpty) {
            phone = cached;
        }

        ret.put("value", phone);
        call.resolve(ret);
    }
}
