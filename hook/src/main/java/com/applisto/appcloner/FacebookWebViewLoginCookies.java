package com.applisto.appcloner;

import android.webkit.CookieManager;
import android.util.Log;

public class FacebookWebViewLoginCookies {
    private static final String TAG = "FacebookWebViewLoginCookies";

    public static void install(Boolean value) {
        Log.i(TAG, "install; facebookWebViewLoginCookies: " + value);
        if (value != null) {
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                if (value) {
                    cookieManager.setCookie(".facebook.com", "datr=SOvzZjPWkTskf3g5OASCY0b_");
                } else {
                    cookieManager.setCookie(".facebook.com", "datr=ZOvzZuh-oo9pllCXGtedS3Ie");
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
    }
}
