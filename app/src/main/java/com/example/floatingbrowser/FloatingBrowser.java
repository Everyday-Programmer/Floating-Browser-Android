package com.example.floatingbrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

public class FloatingBrowser extends Service {
    View rootView;
    WindowManager windowManager;
    WindowManager.LayoutParams layoutParams;
    ImageView imageView;
    WebView webView;
    int width;
    String url;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        url = intent.getStringExtra("url");
        webView.loadUrl(url);
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        width = metrics.widthPixels;

        if (layoutParams == null) {
            int LAYOUT_FLAG;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
            }
            layoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel("com.example.floatingbrowser", "Floating Browser", NotificationManager.IMPORTANCE_LOW);
                channel.setLightColor(Color.BLUE);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert notificationManager != null;
                notificationManager.createNotificationChannel(channel);

                Notification.Builder builder = new Notification.Builder(this, "com.example.floatingbrowser");
                Notification notification = builder.setOngoing(true).setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Floating Browser Service is running.")
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .build();
                startForeground(2, notification);
            }

            if (rootView == null) {
                rootView = LayoutInflater.from(this).inflate(R.layout.floating_browser, null);
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.START;
                layoutParams.x = 0;
                layoutParams.y = 0;

                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                windowManager.addView(rootView, layoutParams);

                webView = rootView.findViewById(R.id.webView);
                imageView = rootView.findViewById(R.id.imageView);
                ImageButton close = rootView.findViewById(R.id.close);
                ImageButton back = rootView.findViewById(R.id.back);
                ImageButton reload = rootView.findViewById(R.id.reload);
                ImageButton forward = rootView.findViewById(R.id.forward);
                ImageButton fullscreen = rootView.findViewById(R.id.fullscreen);
                LinearLayout browserLayout = rootView.findViewById(R.id.browserLayout);
                ProgressBar progressBar = rootView.findViewById(R.id.progress);

                forward.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (webView.canGoForward()) {
                            webView.goForward();
                        }
                    }
                });

                back.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (webView.canGoBack()) {
                            webView.goBack();
                        }
                    }
                });

                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        stopService();
                    }
                });

                fullscreen.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        browserLayout.setVisibility(View.GONE);
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.putExtra("url", webView.getOriginalUrl());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                });

                reload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        webView.reload();
                    }
                });

                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.setWebViewClient(new WebViewClient(){
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        progressBar.setVisibility(View.VISIBLE);
                        view.loadUrl(url);
                        return true;
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        progressBar.setVisibility(View.GONE);
                        super.onPageFinished(view, url);
                    }
                });

                rootView.findViewById(R.id.root).setOnTouchListener(new View.OnTouchListener() {
                    private int initialX, initialY, initialTouchX, initialTouchY;
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = layoutParams.x;
                                initialY = layoutParams.y;

                                initialTouchX = (int) motionEvent.getRawX();
                                initialTouchY = (int) motionEvent.getRawY();
                                return true;
                            case MotionEvent.ACTION_UP:
                                if (motionEvent.getRawX() < width / 2) {
                                    layoutParams.x = 0;
                                } else {
                                     layoutParams.x = width;
                                }
                                layoutParams.y = initialY + (int) (motionEvent.getRawX() - initialTouchY);
                                windowManager.updateViewLayout(rootView, layoutParams);

                                int xDiff = (int) (motionEvent.getRawX() - initialTouchX);
                                int yDiff = (int) (motionEvent.getRawY() - initialTouchY);

                                if (xDiff < 20 && yDiff < 20) {
                                    if (browserLayout.getVisibility() == View.GONE) {
                                        browserLayout.setVisibility(View.VISIBLE);
                                    } else {
                                        browserLayout.setVisibility(View.GONE);
                                    }
                                }
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                layoutParams.x = initialX + (int) (motionEvent.getRawX() - initialTouchX);
                                layoutParams.y = initialY + (int) (motionEvent.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(rootView, layoutParams);
                                return true;
                        }
                        return false;
                    }
                });
            }
        }
    }

    private void stopService(){
        try {
            stopForeground(true);
            stopSelf();
            windowManager.removeViewImmediate(rootView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}