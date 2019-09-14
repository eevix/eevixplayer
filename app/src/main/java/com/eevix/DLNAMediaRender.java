package com.eevix;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class DLNAMediaRender extends Service {
    private static final String TAG = "DLNAMediaRender";
    private static final int STATE_IDLE = PlaybackController.STATE_IDLE;
    private static final int STATE_PREPARING = PlaybackController.STATE_PREPARING;
    private static final int STATE_PAUSED = PlaybackController.STATE_PAUSED;
    private static final int STATE_PLAYING = PlaybackController.STATE_PLAYING;
    private PlaybackController mPlaybackController = null;

    private static native void nativeInit(String friendlyName, String uuid);
    private native void nativeSetup(DLNAMediaRender dlnaMediaRender);
    private native void onStateChanged(int state);

    static {
        System.loadLibrary("dlnamediarender");
        nativeInit(Build.MANUFACTURER + "-" + Build.MODEL, UUID.randomUUID().toString());
    }

    class PlaybackControllerRegister extends Binder {
        void registerPlayerBackController(PlaybackController controller) {
            Log.d(TAG, "PlaybackController controller:" + controller);
            synchronized (DLNAMediaRender.this) {
                mPlaybackController = controller;

                if (mPlaybackController != null) {
                    mPlaybackController.setStateChangedListener(new PlaybackController.StateChangedListener() {
                        @Override
                        public void onStateChanged(int state) {
                            Log.d(TAG, "state:" + state);
                            DLNAMediaRender.this.onStateChanged(state);
                        }
                    });
                }

                DLNAMediaRender.this.notify();
            }
        }
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
        return new PlaybackControllerRegister();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand:" + (intent == null ? "null" : intent) + ", flags:" + flags + ", startId:" + startId);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind:" + (intent == null ? "null" : intent));
        super.onUnbind(intent);
        synchronized (this) {
            mPlaybackController = null;
            onStateChanged(STATE_IDLE);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    private synchronized boolean setDataSource(String url) {
        Log.d(TAG, "setDataSource url:" + url);
        if (mPlaybackController == null) {
            Log.d(TAG, "startActivity");
            Intent intent = new Intent(ACTION_VIEW, Uri.parse(url), this, PlaybackActivity.class);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("from", "DLNAMediaRender");
            startActivity(intent);
        } else {
            mPlaybackController.setDataSource(url);
        }

        if (mPlaybackController == null) {
            try {
                wait(5000);
            } catch (Exception ex) {
                Log.d(TAG, "onAction caught exception:" + ex);
                return false;
            }
        }

        return mPlaybackController != null;
    }

    private synchronized boolean isPlaying() {
        if (mPlaybackController == null) {
            return false;
        }

        return mPlaybackController.isPlaying();
    }

    private synchronized int getCurrentPosition() {
        if (mPlaybackController == null) {
            return 0;
        }

        return mPlaybackController.getCurrentPosition();
    }

    private synchronized int getDuration() {
        if (mPlaybackController == null) {
            return 0;
        }

        return mPlaybackController.getDuration();
    }

    private synchronized void start() {
        if (mPlaybackController != null) {
            mPlaybackController.start();
        }
    }

    private synchronized void stop() {
        if (mPlaybackController != null) {
            mPlaybackController.stop();
        }
    }

    private synchronized void pause() {
        if (mPlaybackController != null) {
            mPlaybackController.pause();
        }
    }

    private synchronized void seek(int millisecond) {
        if (mPlaybackController != null) {
            mPlaybackController.seek(millisecond);
        }
    }
}
