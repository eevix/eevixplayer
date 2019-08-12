package com.eevix;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class DLNAMediaRender extends Service {
    private static final String TAG = "DLNAMediaRender";
    private class Actions {
        private static final int SET_URL = 0;
        private static final int PLAY    = 1;
        private static final int PAUSE   = 2;
        private static final int RESUME  = 3;
        private static final int STOP    = 4;
    }
    private static native void nativeInit();
    private native void nativeSetup(DLNAMediaRender dlnaMediaRender);

    static {
        System.loadLibrary("dlnamediarender");
        nativeInit();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        nativeSetup(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind:" + intent.toString());
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand:" + intent.toString() + ", flags:" + flags + ", startId:" + startId);
        return super.onStartCommand(intent, flags, startId);
    }

    private void onAction(Bundle action) {
        Log.d(TAG, "action:" + action.toString());
        switch (action.getInt("action")) {
            case Actions.SET_URL: {
                Log.d(TAG, "startActivity");
                Intent intent = new Intent(this, PlaybackActivity.class);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(ACTION_VIEW);
                intent.setData(Uri.parse(action.getString("uri")));
                intent.putExtra("from", this.getClass().getName());
                startActivity(intent);
                break;
            }
            default: {
                break;
            }
        }
    }
}
