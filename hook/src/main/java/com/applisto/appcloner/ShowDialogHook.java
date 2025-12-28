package com.applisto.appcloner;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public abstract class ShowDialogHook {
    private static final String TAG = "ShowDialogHook";
    private static final List<ShowDialogHook> HOOKS = new ArrayList<>();
    private static boolean sHooked = false;

    public abstract Boolean handleShowDialog(Dialog dialog);

    public static void install(Context context) {
        if (sHooked) return;
        sHooked = true;

        try {
            Method show = AlertDialog.class.getDeclaredMethod("show");
            Hooking.pineHook(show, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Dialog dialog = (Dialog) callFrame.thisObject;
                    for (ShowDialogHook hook : HOOKS) {
                        try {
                            Boolean result = hook.handleShowDialog(dialog);
                            if (result != null) {
                                if (Boolean.FALSE.equals(result)) {
                                    // Skip show
                                    callFrame.setResult(null);
                                }
                                return;
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            });
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to hook AlertDialog.show", t);
        }
    }

    public static void addHook(ShowDialogHook hook) {
        HOOKS.add(hook);
    }

    public static String getAlertDialogText(Dialog dialog) {
        StringBuilder sb = new StringBuilder();
        try {
            if (dialog instanceof AlertDialog) {
                // Try to get message/title via reflection or standard API
                // AlertDialog doesn't publicly expose getMessage/getTitle easily in older APIs without reflection on mAlert

                // 1. Try public window title
                if (dialog.getWindow() != null) {
                    CharSequence title = dialog.getWindow().getAttributes().getTitle();
                    if (title != null) sb.append(title).append(" ");
                }

                // 2. Decor view traversal (aggressive)
                if (dialog.getWindow() != null) {
                    View decor = dialog.getWindow().getDecorView();
                    extractTextFromView(decor, sb);
                }
            } else {
                 if (dialog.getWindow() != null) {
                    View decor = dialog.getWindow().getDecorView();
                    extractTextFromView(decor, sb);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return sb.toString().trim().toLowerCase(Locale.ENGLISH);
    }

    private static void extractTextFromView(View v, StringBuilder sb) {
        if (v == null) return;
        if (v.getVisibility() != View.VISIBLE) return;

        if (v instanceof TextView) {
            CharSequence txt = ((TextView) v).getText();
            if (!TextUtils.isEmpty(txt)) {
                sb.append(txt).append(" ");
            }
        }

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                extractTextFromView(vg.getChildAt(i), sb);
            }
        }
    }
}
