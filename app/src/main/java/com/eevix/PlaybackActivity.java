package com.eevix;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PlaybackActivity extends Activity {
    private static final String TAG = "PlaybackActivity";
    private static final int mMediaControllerBarVisibleTime = 5000; // ms
    private String          mUrl;
    private SurfaceView     mSurfaceView;
    private View            mDisplayLayout;
    private MediaPlayer     mMediaPlayer;
    private Handler         mMainHandler;
    private Handler         mPlayerHandler;
    private HandlerThread   mPlayerHandlerThread;
    private PlayerListener  mPlayerListener;
    private MediaControllerBar mMediaControllerBar;
    private boolean         mSurfaceValid = false;
    private int             mVideoWidth = 0;
    private int             mVideoHeight = 0;
    private enum MessageType {
        START,
        PAUSE,
        STOP,
        SEEK,
        PREPARED,
        COMPLETED,
        SURFACE_CREATED,
        SURFACE_DESTROYED,
        PAUSED,
        STARTED,
        STOPPED,
        UPDATE,
        UPDATE_VIDEO_SIZE,
        SHOW_CONTROLLER_BAR,
        HIDE_CONTROLLER_BAR;

        public int value() {
            return ordinal();
        }

        public static MessageType valueOf(int value) {
            if (value > values().length) {
                return null;
            }

            return values()[value];
        }
    }

    private class SurfaceHolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated");
            mPlayerHandler.sendEmptyMessage(MessageType.SURFACE_CREATED.value());
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged, width:" + width + ", height:" + height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed");
            mPlayerHandler.sendEmptyMessage(MessageType.SURFACE_DESTROYED.value());
        }
    }

    private class PlayerListener implements MediaPlayer.OnPreparedListener,
                                    MediaPlayer.OnCompletionListener,
                                    MediaPlayer.OnSeekCompleteListener,
                                    MediaPlayer.OnVideoSizeChangedListener {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mPlayerHandler.sendEmptyMessage(MessageType.COMPLETED.value());
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            mPlayerHandler.sendEmptyMessage(MessageType.PREPARED.value());
        }

        @Override
        public void onSeekComplete(MediaPlayer mp) {
            mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
            mPlayerHandler.sendEmptyMessage(MessageType.START.value());
        }

        @Override
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            Log.d(TAG, "onVideoSizeChanged, width:" + width + ", height:" + height);
            Message message = mMainHandler.obtainMessage(MessageType.UPDATE_VIDEO_SIZE.value());
            Bundle bundle = new Bundle();
            bundle.putInt("video-width", width);
            bundle.putInt("video-height", height);
            message.setData(bundle);
            message.sendToTarget();
        }
    }

    private class PlaybackControlListener implements MediaControllerBar.OnPlaybackControlListener {
        @Override
        public void onPlay(boolean play) {
            if (play) {
                mPlayerHandler.sendEmptyMessage(MessageType.START.value());
            } else {
                mPlayerHandler.sendEmptyMessage(MessageType.PAUSE.value());
            }
        }

        @Override
        public void onSeek(int position) {
            mPlayerHandler.removeMessages(MessageType.UPDATE.value());
            mPlayerHandler.obtainMessage(MessageType.SEEK.value(), position, 0).sendToTarget();
            mMainHandler.removeMessages(MessageType.UPDATE.value());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        mSurfaceView = findViewById(R.id.surface_view);
        mDisplayLayout = findViewById(R.id.display_layout);
        mDisplayLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    Log.d(TAG, "onLayoutChange view:" + v + ", left:" + left + ", top:" + top + ", right:" + right + ", bottom:" + bottom);
                    mMainHandler.sendEmptyMessage(MessageType.UPDATE_VIDEO_SIZE.value());
                }
            }
        });
        mMediaControllerBar = findViewById(R.id.media_controller_bar);
        mMediaControllerBar.setVisibility(View.VISIBLE);
        mMediaControllerBar.setListener(new PlaybackControlListener());
        mMainHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return handleMainThreadMessage(msg);
            }
        });
        mSurfaceView.getHolder().addCallback(new SurfaceHolderCallback());
        mPlayerListener = new PlayerListener();

        mPlayerHandlerThread = new HandlerThread("playerThread");
        mPlayerHandlerThread.start();
        mPlayerHandler = new Handler(mPlayerHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handlePlayerThreadMessage(msg);
            }
        };
        mMainHandler.sendEmptyMessageDelayed(MessageType.HIDE_CONTROLLER_BAR.value(), mMediaControllerBarVisibleTime);
        Log.d(TAG, "original orientation:" + getResources().getConfiguration().orientation);
        Log.d(TAG, "displayLayoutWidth:" + mDisplayLayout.getMeasuredWidth() + ", displayLayoutHeight:" + mDisplayLayout.getMeasuredHeight());

        onIntent(getIntent());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            mMainHandler.removeMessages(MessageType.HIDE_CONTROLLER_BAR.value());

            if (mMediaControllerBar.getVisibility() != View.VISIBLE) {
                mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
                mMediaControllerBar.setVisibility(View.VISIBLE);
                return true;
            }
        } else {
            mMainHandler.removeMessages(MessageType.HIDE_CONTROLLER_BAR.value());
            mMainHandler.sendEmptyMessageDelayed(MessageType.HIDE_CONTROLLER_BAR.value(), mMediaControllerBarVisibleTime);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (mPlayerHandler != null) {
            mPlayerHandler.sendEmptyMessage(MessageType.STOP.value());
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mPlayerHandlerThread != null) {
            mPlayerHandlerThread.quitSafely();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent:" + intent.toString());
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        Log.d(TAG, "onIntent:" + intent);
        String action = intent.getAction();
        String data = intent.getDataString();
        if (action != null && action.equals(Intent.ACTION_VIEW)) {
            boolean ret = mPlayerHandler.sendMessage(mPlayerHandler.obtainMessage(MessageType.START.value(), data));
            Log.d(TAG, "play:" + (data == null ? "" : data));
            Log.d(TAG, "ret:" + ret);
        }
    }

    private boolean handleMainThreadMessage(Message msg) {
        MessageType message = MessageType.valueOf(msg.what);
        if (message == null) {
            return false;
        }
        Log.d(TAG, "handleMainThreadMessage:" + message);
        switch (message) {
            case PAUSED: {
                mMediaControllerBar.setIsPlaying(false);
                break;
            }
            case STARTED: {
                mMediaControllerBar.setIsPlaying(true);
                Bundle bundle = msg.getData();
                if (bundle.containsKey("duration")) {
                    int duration = bundle.getInt("duration");
                    Log.d(TAG, "duration:" + duration);
                    if (duration >= 0) {
                        mMediaControllerBar.setDuration(duration);
                    }
                }
                break;
            }
            case STOPPED: {
                mMediaControllerBar.reset();
                mVideoWidth = 0;
                mVideoHeight = 0;
                mSurfaceView.getHolder().setSizeFromLayout();
                break;
            }
            case UPDATE: {
                Bundle bundle = msg.getData();
                if (bundle.containsKey("currentTime")) {
                    int currentTime = bundle.getInt("currentTime");
                    if (currentTime >= 0) {
                        mMediaControllerBar.setCurrentPosition(currentTime);
                    }
                }
                break;
            }
            case UPDATE_VIDEO_SIZE: {
                Bundle bundle = msg.getData();

                if (bundle.containsKey("video-width") && bundle.containsKey("video-height")) {
                    mVideoWidth = bundle.getInt("video-width");
                    mVideoHeight = bundle.getInt("video-height");
                }

                if (mVideoWidth <= 0 || mVideoHeight <= 0) {
                    mSurfaceView.getHolder().setSizeFromLayout();
                }  else if (mVideoWidth / mVideoHeight > mDisplayLayout.getMeasuredWidth() / mDisplayLayout.getMeasuredHeight()) {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredWidth(), mDisplayLayout.getMeasuredWidth() * mVideoHeight / mVideoWidth);
                } else {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredHeight() * mVideoWidth / mVideoHeight, mDisplayLayout.getMeasuredHeight());
                }

                break;
            }
            case HIDE_CONTROLLER_BAR: {
                mMediaControllerBar.setVisibility(View.GONE);
                mPlayerHandler.removeMessages(MessageType.UPDATE.value());
                break;
            }
            default: {
                break;
            }
        }

        return true;
    }

    private void handlePlayerThreadMessage(Message msg) {
        MessageType messageType = MessageType.valueOf(msg.what);
        if (messageType == null) {
            return;
        }
        Log.d(TAG, "handlePlayerThreadMessage:" + messageType);
        switch (messageType) {
            case START: {
                if (msg.obj instanceof String) {
                    play((String)msg.obj);
                } else if (mMediaPlayer != null) {
                    try {
                            mMediaPlayer.start();
                            mMainHandler.sendEmptyMessage(MessageType.STARTED.value());
                            mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
                    } catch (Exception exception) {
                        Log.e(TAG, "exception:", exception);
                    }
                } else if (mUrl != null) {
                    play(mUrl);
                }
                break;
            }
            case PAUSE: {
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.pause();
                        mMainHandler.sendEmptyMessage(MessageType.PAUSED.value());
                    }
                } catch (Exception exception) {
                    Log.e(TAG, "exception:", exception);
                }
                break;
            }
            case STOP: {
                stop();
                mMainHandler.sendEmptyMessage(MessageType.STOPPED.value());
                mMainHandler.removeMessages(MessageType.UPDATE.value());
                break;
            }
            case SEEK: {
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.seekTo(msg.arg1);
                    }
                } catch (Exception exception) {
                    Log.e(TAG, "exception:", exception);
                }
                break;
            }
            case PREPARED: {
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.start();
                        Log.e(TAG, "mMediaPlayer.start()");
                        Message message = mMainHandler.obtainMessage(MessageType.STARTED.value());
                        Bundle bundle = new Bundle();
                        bundle.putInt("duration", mMediaPlayer.getDuration());
                        bundle.putInt("video-width", mMediaPlayer.getVideoWidth());
                        bundle.putInt("video-height", mMediaPlayer.getVideoHeight());
                        message.setData(bundle);
                        message.sendToTarget();
                        mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
                    }
                } catch (Exception exception) {
                    Log.e(TAG, "exception:", exception);
                }
                break;
            }
            case COMPLETED: {
                stop();
                mMainHandler.sendEmptyMessage(MessageType.STOPPED.value());
                mMainHandler.removeMessages(MessageType.UPDATE.value());
                break;
            }
            case SURFACE_CREATED: {
                Log.e(TAG, "SURFACE_CREATED");
                mSurfaceValid = true;
                if (mMediaPlayer != null) {
                    Log.e(TAG, "mMediaPlayer.setDisplay()");
                    mMediaPlayer.setDisplay(mSurfaceView.getHolder());
                }
                break;
            }
            case SURFACE_DESTROYED: {
                Log.e(TAG, "SURFACE_DESTROYED");
                mSurfaceValid = false;
                stop();
                mMainHandler.removeMessages(MessageType.UPDATE.value());
                break;
            }
            case UPDATE: {
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        Message message = mMainHandler.obtainMessage(MessageType.UPDATE.value());
                        Bundle bundle = new Bundle();
                        bundle.putInt("currentTime", mMediaPlayer.getCurrentPosition());
                        message.setData(bundle);
                        message.sendToTarget();
                        mPlayerHandler.removeMessages(MessageType.UPDATE.value());
                        mPlayerHandler.sendEmptyMessageDelayed(MessageType.UPDATE.value(), 500);
                    }
                }
                break;
            }
            case UPDATE_VIDEO_SIZE: {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    Message message = mMainHandler.obtainMessage(MessageType.UPDATE_VIDEO_SIZE.value());
                    Bundle bundle = new Bundle();
                    bundle.putInt("video-width", mMediaPlayer.getVideoWidth());
                    bundle.putInt("video-height", mMediaPlayer.getVideoHeight());
                    message.setData(bundle);
                    message.sendToTarget();
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    private void play(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        Log.d(TAG, path);
        /* stop playback first*/
        stop();

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(mPlayerListener);
        mMediaPlayer.setOnCompletionListener(mPlayerListener);
        mMediaPlayer.setOnSeekCompleteListener(mPlayerListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mPlayerListener);

        try {
            Log.e(TAG, "mMediaPlayer.setDataSource()");
            mMediaPlayer.setDataSource(path);
            if (mSurfaceValid) {
                Log.e(TAG, "mMediaPlayer.setDisplay()");
                mMediaPlayer.setDisplay(mSurfaceView.getHolder());
            }
            Log.e(TAG, "mMediaPlayer.prepareAsync()");
            mMediaPlayer.prepareAsync();
        } catch (Exception exception) {
            Log.e(TAG, "exception:" + exception);
            mMediaPlayer.release();
        }
        mUrl = path;
    }

    private void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private String millisecondsToTime(int milliseconds) {
        return String.format("%02d:%02d:%02d", milliseconds / 1000 / 3600, milliseconds / 1000 / 60 % 60, milliseconds / 1000 % 60);
    }
}
