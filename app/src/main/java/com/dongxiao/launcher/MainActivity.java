package com.dongxiao.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Process;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 壁纸桌面：一个全屏 WebView 承载界面（assets/index.html），
 * 原生层只负责三件事：列出手机里的软件、打开软件、处理默认桌面。
 */
public class MainActivity extends Activity {

    private static final int REQ_HOME = 7;
    private WebView web;
    /** 本次打开已经请求过设为默认桌面，用户拒绝后不再反复弹窗 */
    private boolean askedHome = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);

        web = new WebView(this);
        web.setBackgroundColor(0xFF12141C);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Bridge(), "Launcher");
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        // 软件逻辑：打开即成为默认桌面
        ensureDefaultHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到桌面时刷新最近使用和软件列表
        if (web != null) js("window.onResumed && window.onResumed()");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 从软件图标重新打开时，再请求一次成为默认桌面；按主页键回来时不打扰
        if (intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            askedHome = false;
            ensureDefaultHome();
        }
    }

    private boolean isDefaultHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
    }

    private void ensureDefaultHome() {
        if (isDefaultHome() || askedHome) return;
        askedHome = true;
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME);
                return;
            }
        }
        openHomeSettings();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_HOME) js("window.onHomeRole && window.onHomeRole(" + isDefaultHome() + ")");
    }

    /** 桌面不能被返回键退出：交给页面收起卡片或菜单 */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        js("window.onAndroidBack && window.onAndroidBack()");
    }

    private void js(final String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }

    private String iconData(Drawable d) {
        int n = 144;
        Bitmap bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, out);
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** 页面通过 window.Launcher 调用 */
    class Bridge {
        /** 后台读取所有带图标的软件，读完调用页面里的 cb(jsonString) */
        @JavascriptInterface
        public void loadApps(final String cb) {
            if (!cb.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) return;
            new Thread(() -> {
                JSONArray arr = new JSONArray();
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                for (ResolveInfo r : list) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    try {
                        JSONObject o = new JSONObject();
                        o.put("id", pkg + "/" + r.activityInfo.name);
                        o.put("label", String.valueOf(r.loadLabel(pm)));
                        o.put("icon", iconData(r.loadIcon(pm)));
                        arr.put(o);
                    } catch (Exception ignored) {
                    }
                }
                js(cb + "(" + JSONObject.quote(arr.toString()) + ")");
            }).start();
        }

        /** id = 包名/Activity 类名 */
        @JavascriptInterface
        public void launch(String id) {
            int k = id.indexOf('/');
            if (k <= 0) return;
            String pkg = id.substring(0, k), cls = id.substring(k + 1);
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setClassName(pkg, cls);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    startActivity(i);
                } catch (Exception e) {
                    Intent l = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (l != null) startActivity(l);
                }
            });
        }

        /** 退出桌面：打开系统的默认桌面设置，选回手机自带的桌面 */
        @JavascriptInterface
        public void exitDesktop() {
            runOnUiThread(MainActivity.this::openHomeSettings);
        }

        @JavascriptInterface
        public boolean isDefault() {
            return isDefaultHome();
        }

        /** 是否已开启「使用情况访问权限」 */
        @JavascriptInterface
        public boolean hasUsageAccess() {
            AppOpsManager ops = getSystemService(AppOpsManager.class);
            if (ops == null) return false;
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        }

        @JavascriptInterface
        public void openUsageAccess() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                }
            });
        }

        /** 最近 3 天用过的、有桌面图标的软件，按最后使用时间从新到旧，最多 30 个 */
        @JavascriptInterface
        public void loadRecents(final String cb) {
            if (!cb.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) return;
            new Thread(() -> {
                JSONArray arr = new JSONArray();
                try {
                    UsageStatsManager usm = getSystemService(UsageStatsManager.class);
                    long end = System.currentTimeMillis(), start = end - 3L * 24 * 3600 * 1000;
                    Map<String, Long> last = new HashMap<>();
                    UsageEvents ev = usm.queryEvents(start, end);
                    UsageEvents.Event e = new UsageEvents.Event();
                    while (ev.hasNextEvent()) {
                        ev.getNextEvent(e);
                        if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last.put(e.getPackageName(), e.getTimeStamp());
                    }
                    PackageManager pm = getPackageManager();
                    Intent li = new Intent(Intent.ACTION_MAIN);
                    li.addCategory(Intent.CATEGORY_LAUNCHER);
                    Map<String, ResolveInfo> launchable = new HashMap<>();
                    for (ResolveInfo r : pm.queryIntentActivities(li, 0)) launchable.putIfAbsent(r.activityInfo.packageName, r);
                    List<Map.Entry<String, Long>> list = new ArrayList<>(last.entrySet());
                    list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                    int n = 0;
                    for (Map.Entry<String, Long> en : list) {
                        String pkg = en.getKey();
                        if (pkg.equals(getPackageName())) continue;
                        ResolveInfo r = launchable.get(pkg);
                        if (r == null) continue;
                        JSONObject o = new JSONObject();
                        o.put("id", pkg + "/" + r.activityInfo.name);
                        o.put("label", String.valueOf(r.loadLabel(pm)));
                        o.put("icon", iconData(r.loadIcon(pm)));
                        o.put("time", en.getValue());
                        arr.put(o);
                        if (++n >= 30) break;
                    }
                } catch (Exception ignored) {
                }
                js(cb + "(" + JSONObject.quote(arr.toString()) + ")");
            }).start();
        }

        /**
         * 以悬浮窗打开：从图标位置放大启动，窗口边界停在胶囊上方。
         * 手机不支持悬浮窗（freeform）时，系统忽略边界，按全屏打开。
         */
        @JavascriptInterface
        public void launchFloating(String id, int ix, int iy, int iw, int ih, int l, int t, int r, int b) {
            int k = id.indexOf('/');
            if (k <= 0) return;
            String pkg = id.substring(0, k), cls = id.substring(k + 1);
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setClassName(pkg, cls);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                ActivityOptions o = ActivityOptions.makeScaleUpAnimation(web, ix, iy, Math.max(1, iw), Math.max(1, ih));
                o.setLaunchBounds(new Rect(l, t, r, b));
                try {   // 5 = WINDOWING_MODE_FREEFORM；不可用时忽略
                    Method m = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
                    m.invoke(o, 5);
                } catch (Throwable ignored) {
                }
                try {
                    startActivity(i, o.toBundle());
                } catch (Exception e) {
                    Intent fb = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (fb != null) startActivity(fb);
                }
            });
        }
    }
}
