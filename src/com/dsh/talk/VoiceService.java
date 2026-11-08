package com.dsh.talk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 常驻前台服务：保证进程不被系统回收，MCP 才能在后台一直可用。
 * 通知栏同时作为状态显示（待机 / 有 AI 在等候）。
 */
public class VoiceService extends Service {

    private static final String CH = "call";
    private static final int ID = 42;

    private static volatile boolean running = false;
    private static volatile String stateText = "正在后台运行";

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, VoiceService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            running = false;
            c.stopService(new Intent(c, VoiceService.class));
        } catch (Exception ignored) {
        }
    }

    /**
     * 更新通知栏文字。直接在已有通知上刷新，不重启服务——
     * 避免 Android 12+ 后台 startForegroundService 被拒。
     */
    public static void update(Context c, String text) {
        if (text == null) return;
        stateText = text;
        if (!running) return;
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID, buildNoti(c));
        } catch (Exception ignored) {
        }
    }

    public static boolean isRunning() {
        return running;
    }

    private static Notification buildNoti(Context c) {
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(CH, "语伴状态",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.setDescription("显示语伴的运行状态");
                nm.createNotificationChannel(ch);
            }
        } catch (Exception ignored) {
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, pf);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH)
                : new Notification.Builder(c);
        b.setContentTitle("语伴 · 运行中")
                .setContentText(stateText)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false);
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String t = intent != null ? intent.getStringExtra("text") : null;
            if (t != null) stateText = t;
            startForeground(ID, buildNoti(this));
            running = true;
        } catch (Exception ignored) {
        }
        return START_STICKY;   // 被杀后尽量自动恢复，保证 MCP 不断
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
