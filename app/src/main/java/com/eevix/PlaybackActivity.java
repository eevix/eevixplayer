package com.eevix;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class PlaybackActivity extends Activity {
    private static final String TAG = "PlaybackActivity";
    private static final int mMediaControllerBarVisibleTime = 5000; // ms
    private String          mUrl;
    private int             mLastPosition = 0;
    private SurfaceView     mSurfaceView;
    private View            mDisplayLayout;
    private MediaPlayer     mMediaPlayer;
    private Handler         mPlayerHandler;
    private HandlerThread   mPlayerHandlerThread;
    private MediaPlayerListener mMediaPlayerListener;
    private MediaControllerBar mMediaControllerBar;
    private boolean         mSurfaceValid = false;
    private DLNAMediaRender.PlaybackControllerRegister mPlaybackControllerRegister;
    private DLNAMediaRenderConnection mDLNAMediaRenderConnection = null;
    private Controller      mController = new Controller();
    private PlayerState     mState = PlayerState.IDLE;

    enum PlayerState {
        IDLE,
        PREPARING,
        PLAYING,
        PAUSED,
    }

    private class Controller implements PlaybackController {
        private StateChangedListener mStateChangedListener = null;

        @Override
        public void setDataSource(String url) {
            Log.d(TAG, "Controller: setDataSource:" + url);
            mPlayerHandler.obtainMessage(MessageType.SET_DATA_SOURCE.value(), url).sendToTarget();
        }

        @Override
        public void start() {
            Log.d(TAG, "Controller: start");
            mPlayerHandler.sendEmptyMessage(MessageType.START.value());
        }

        @Override
        public void pause() {
            Log.d(TAG, "Controller: pause");
            mPlayerHandler.sendEmptyMessage(MessageType.PAUSE.value());
        }

        @Override
        public void resume() {
            Log.d(TAG, "Controller: resume");
            mPlayerHandler.sendEmptyMessage(MessageType.START.value());
        }

        @Override
        public void stop() {
            Log.d(TAG, "Controller: stop");
            mPlayerHandler.sendEmptyMessage(MessageType.STOP.value());
        }

        @Override
        public void seek(int millisecond) {
            Log.d(TAG, "Controller: seek:" + millisecond);
            mPlayerHandler.obtainMessage(MessageType.SEEK.value(), millisecond, 0).sendToTarget();
        }

        @Override
        public int getCurrentPosition() {
            MessageReply<Integer> reply = new MessageReply<>();
            mPlayerHandler.obtainMessage(MessageType.GET_CURRENT_POSITION.value(), reply).sendToTarget();
            reply.waitReply();
            return reply.getData();
        }

        @Override
        public int getDuration() {
            MessageReply<Integer> reply = new MessageReply<>();
            mPlayerHandler.obtainMessage(MessageType.GET_DURATION.value(), reply).sendToTarget();
            reply.waitReply();
            Log.d(TAG, "Controller: getDuration:" + reply.getData());
            return reply.getData();
        }

        @Override
        public boolean isPlaying() {
            MessageReply<PlayerState> reply = new MessageReply<>();
            mPlayerHandler.obtainMessage(MessageType.QUERY_STATE.value(), reply).sendToTarget();
            reply.waitReply();
            return reply.getData() == PlayerState.PLAYING;
        }

        @Override
        public int getState() {
            MessageReply<PlayerState> reply = new MessageReply<>();
            mPlayerHandler.obtainMessage(MessageType.QUERY_STATE.value(), reply).sendToTarget();
            reply.waitReply();
            return convertState(reply.getData());
        }

        @Override
        public void setStateChangedListener(StateChangedListener listener) {
            mStateChangedListener = listener;
        }

        void changeState(int state) {
            Log.d(TAG, "Controller: changeState:" + state);
            StateChangedListener listener = mStateChangedListener;
            if (listener != null) {
                listener.onStateChanged(state);
            }
        }
    }

    private enum MessageType {
        SET_DATA_SOURCE,
        START,
        PAUSE,
        STOP,
        SEEK,
        GET_CURRENT_POSITION,
        GET_DURATION,
        QUERY_STATE,
        PREPARED,
        COMPLETED,
        SURFACE_CREATED,
        SURFACE_DESTROYED,
        SHOW_CONTROLLER_BAR,
        HIDE_CONTROLLER_BAR,
        UPDATE,
        UPDATE_DISPLAY_REGION;

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
            Log.d(TAG, "SurfaceHolderCallback: surfaceCreated");
            mPlayerHandler.sendEmptyMessage(MessageType.SURFACE_CREATED.value());
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "SurfaceHolderCallback: surfaceChanged, width:" + width + ", height:" + height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "SurfaceHolderCallback: surfaceDestroyed");
            mPlayerHandler.sendEmptyMessage(MessageType.SURFACE_DESTROYED.value());
        }
    }

    private class MediaPlayerListener implements MediaPlayer.OnPreparedListener,
                                                 MediaPlayer.OnCompletionListener,
                                                 MediaPlayer.OnSeekCompleteListener,
                                                 MediaPlayer.OnVideoSizeChangedListener {
        @Override
        public void onCompletion(MediaPlayer mp) {
            Log.d(TAG, "MediaPlayerListener: onCompletion");
            mPlayerHandler.sendEmptyMessage(MessageType.COMPLETED.value());
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            Log.d(TAG, "MediaPlayerListener: onPrepared");
            mPlayerHandler.obtainMessage(MessageType.PREPARED.value(), mp).sendToTarget();
        }

        @Override
        public void onSeekComplete(MediaPlayer mp) {
            Log.d(TAG, "MediaPlayerListener: onSeekComplete");
            mPlayerHandler.sendEmptyMessage(MessageType.START.value());
        }

        @Override
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            Log.d(TAG, "MediaPlayerListener: onVideoSizeChanged, width:" + width + ", height:" + height);
            mPlayerHandler.obtainMessage(MessageType.UPDATE_DISPLAY_REGION.value(), width, height).sendToTarget();
        }
    }

    private class PlaybackControlListener implements MediaControllerBar.OnPlaybackControlListener {
        @Override
        public void onPlay(boolean play) {
            Log.d(TAG, "PlaybackControlListener: onPlay:" + play);
            if (play) {
                mController.start();
            } else {
                mController.pause();
            }
        }

        @Override
        public void onSeek(int position) {
            Log.d(TAG, "PlaybackControlListener: onSeek:" + position);
            mController.seek(position);
        }
    }

    private class DLNAMediaRenderConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "DLNAMediaRenderConnection: onServiceConnected, name:" + name + ", service:" + service);
            if (service instanceof DLNAMediaRender.PlaybackControllerRegister) {
                mPlaybackControllerRegister = (DLNAMediaRender.PlaybackControllerRegister) service;
                mPlaybackControllerRegister.registerPlayerBackController(mController);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "DLNAMediaRenderConnection: onServiceDisconnected, name:" + name);
            mController.setStateChangedListener(null);
            mController.stop();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "DLNAMediaRenderConnection: onBindingDied, name:" + name);
            mController.setStateChangedListener(null);
            mController.stop();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "DLNAMediaRenderConnection: onNullBinding, name:" + name);
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
                    mPlayerHandler.sendEmptyMessage(MessageType.UPDATE_DISPLAY_REGION.value());
                }
            }
        });
        mMediaControllerBar = findViewById(R.id.media_controller_bar);
        mMediaControllerBar.setVisibility(View.VISIBLE);
        mMediaControllerBar.setListener(new PlaybackControlListener());

        mSurfaceView.getHolder().addCallback(new SurfaceHolderCallback());
        mMediaPlayerListener = new MediaPlayerListener();
        mPlayerHandlerThread = new HandlerThread("playerThread");

        mPlayerHandlerThread.start();
        mPlayerHandler = new Handler(mPlayerHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                PlaybackActivity.this.handleMessage(msg);
            }
        };
        onIntent(getIntent());
        setIntent(null);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            mPlayerHandler.sendEmptyMessage(MessageType.SHOW_CONTROLLER_BAR.value());
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

        if (mDLNAMediaRenderConnection != null) {
            unbindService(mDLNAMediaRenderConnection);
            mController.setStateChangedListener(null);
            mDLNAMediaRenderConnection = null;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.sendEmptyMessage(MessageType.STOP.value());
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart");
        super.onRestart();
        mController.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mPlayerHandlerThread != null) {
            mPlayerHandlerThread.quitSafely();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent:" + intent.toString());
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Log.d(TAG, "onIntent:" + intent);
        String action = intent.getAction();
        String data = intent.getDataString();
        String from = intent.getStringExtra("from");

        if (from !=null && from.equals("DLNAMediaRender")) {
            Intent intent2 = new Intent();
            mDLNAMediaRenderConnection = new DLNAMediaRenderConnection();
            intent2.setClass(this, DLNAMediaRender.class);
            if (!bindService(intent2, mDLNAMediaRenderConnection, BIND_AUTO_CREATE)) {
                Log.e(TAG, "bindService failed");
                mDLNAMediaRenderConnection = null;
            }
        }

        if (action != null && action.equals(Intent.ACTION_VIEW)) {
            mController.setDataSource(data);
        }
    }

    private void handleMessage(Message msg) {
        MessageType message = MessageType.valueOf(msg.what);
        if (message == null) {
            return;
        }

        if (message != MessageType.UPDATE
            && message != MessageType.GET_CURRENT_POSITION
            && message != MessageType.QUERY_STATE) {
            Log.d(TAG, "handleMessage:" + message);
        }

        switch (message) {
            case SET_DATA_SOURCE: {
                setDataSourceAndPrepare((String) msg.obj);
                break;
            }
            case START: {
                start();
                break;
            }
            case PAUSE: {
                pause();
                break;
            }
            case STOP: {
                stop();
                break;
            }
            case SEEK: {
                seekTo(msg.arg1);
                break;
            }
            case GET_CURRENT_POSITION: {
                MessageReply<Integer> reply = ((MessageReply<Integer>) msg.obj);
                reply.setData(getCurrentPosition());
                reply.notifyReply();
                break;
            }
            case GET_DURATION: {
                MessageReply<Integer> reply = ((MessageReply<Integer>) msg.obj);
                reply.setData(getDuration());
                reply.notifyReply();
                break;
            }
            case QUERY_STATE: {
                MessageReply<PlayerState> reply = ((MessageReply<PlayerState>) msg.obj);
                reply.setData(mState);
                reply.notifyReply();
                break;
            }
            case PREPARED: {
                onPrepared((MediaPlayer) msg.obj);
                break;
            }
            case COMPLETED: {
                onCompleted();
                break;
            }
            case SURFACE_CREATED: {
                onSurfaceCreated();
                break;
            }
            case SURFACE_DESTROYED: {
                onSurfaceDestroyed();
                break;
            }
            case SHOW_CONTROLLER_BAR: {
                showMediaControllerBar(true);
                break;
            }
            case HIDE_CONTROLLER_BAR: {
                showMediaControllerBar(false);
                break;
            }
            case UPDATE: {
                update();
                break;
            }
            case UPDATE_DISPLAY_REGION: {
                updateDisplayRegion();
                break;
            }
            default: {
                break;
            }
        }
    }

    private void setDataSourceAndPrepare(String url) {
        Log.d(TAG, "setDataSourceAndPrepare");
        if (url == null || url.isEmpty()) {
            return;
        }

        Log.d(TAG, url);
        /* stop playback first*/
        MediaPlayer mediaPlayer = new MediaPlayer();
        stop();

        mMediaPlayer = mediaPlayer;
        Log.d(TAG, "mediaPlayer:" + mediaPlayer.hashCode());
        mMediaPlayer.setOnPreparedListener(mMediaPlayerListener);
        mMediaPlayer.setOnCompletionListener(mMediaPlayerListener);
        mMediaPlayer.setOnSeekCompleteListener(mMediaPlayerListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mMediaPlayerListener);

        try {
            Log.e(TAG, "mMediaPlayer.setDataSource(" + url + ")");
            mMediaPlayer.setDataSource(url);
            if (mSurfaceValid) {
                Log.e(TAG, "mMediaPlayer.setDisplay()");
                mMediaPlayer.setDisplay(mSurfaceView.getHolder());
                mMediaPlayer.setScreenOnWhilePlaying(true);
            }
            Log.e(TAG, "mMediaPlayer.prepareAsync()");
            mMediaPlayer.prepareAsync();
            changeState(PlayerState.PREPARING);
        } catch (Exception exception) {
            Log.e(TAG, "exception:" + exception);
            mMediaPlayer.release();
        }

        mUrl = url;
        mLastPosition = 0;
    }

    private void start() {
        Log.d(TAG, "start");
        if (mMediaPlayer != null && mState == PlayerState.PAUSED) {
            try {
                mMediaPlayer.start();
                mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
                changeState(PlayerState.PLAYING);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaControllerBar.setIsPlaying(true);
                    }
                });
            } catch (Exception exception) {
                Log.e(TAG, "exception:", exception);
            }
        } else if (mUrl != null && mState == PlayerState.IDLE) {
            setDataSourceAndPrepare(mUrl);
        }
    }

    private void pause() {
        Log.d(TAG, "pause");
        try {
            if (mState == PlayerState.PLAYING && mMediaPlayer != null) {
                mMediaPlayer.pause();
                changeState(PlayerState.PAUSED);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaControllerBar.setIsPlaying(false);
                    }
                });
            }
        } catch (Exception exception) {
            Log.e(TAG, "exception:", exception);
        }
    }

    private void stop() {
        Log.d(TAG, "stop");
        if (mState == PlayerState.PLAYING || mState == PlayerState.PAUSED) {
            mMediaPlayer.pause();
            mLastPosition = mMediaPlayer.getCurrentPosition();
            Log.d(TAG, "got last position:" + mLastPosition);
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.setOnPreparedListener(null);
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.setOnVideoSizeChangedListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        changeState(PlayerState.IDLE);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMediaControllerBar.reset();
            }
        });

        mPlayerHandler.removeMessages(MessageType.UPDATE.value());
    }

    private void seekTo(int position) {
        Log.d(TAG, "seekTo:" + position);
        try {
            if (mMediaPlayer != null && (mState == PlayerState.PLAYING || mState == PlayerState.PAUSED)) {
                mMediaPlayer.seekTo(position);
            }
        } catch (Exception exception) {
            Log.e(TAG, "exception:", exception);
        }
    }

    private int getCurrentPosition() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getCurrentPosition();
        }

        return 0;
    }

    private int getDuration() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getDuration();
        }

        return 0;
    }

    private void onPrepared(MediaPlayer mediaPlayer) {
        Log.d(TAG, "onPrepared, player:" + mediaPlayer.hashCode());
        if (mMediaPlayer != mediaPlayer) {
            return;
        }

        try {
            if (mLastPosition > 0 && mLastPosition < mMediaPlayer.getDuration()) {
                Log.d(TAG, "seekTo last position:" + mLastPosition);
                mMediaPlayer.seekTo(mLastPosition);
            }

            mMediaPlayer.start();
        } catch (IllegalStateException ex) {
            return;
        }

        changeState(PlayerState.PLAYING);
        mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
        updateDisplayRegion();

        final int duration = mMediaPlayer.getDuration();
        final int position = mMediaPlayer.getCurrentPosition();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMediaControllerBar.setDuration(duration);
                mMediaControllerBar.setCurrentPosition(position);
                mMediaControllerBar.setIsPlaying(true);
            }
        });
    }

    private void onCompleted() {
        Log.d(TAG, "onCompleted");
        stop();
        mLastPosition = 0;
        Log.d(TAG, "reset last position:" + mLastPosition);
    }

    private void onSurfaceCreated() {
        Log.d(TAG, "onSurfaceCreated");
        mSurfaceValid = true;
        if (mMediaPlayer != null) {
            Log.e(TAG, "mMediaPlayer.setDisplay()");
            mMediaPlayer.setDisplay(mSurfaceView.getHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);
        }
    }

    private void onSurfaceDestroyed() {
        Log.d(TAG, "onSurfaceDestroyed");
        mSurfaceValid = false;
        if (mState == PlayerState.PLAYING) {
            mMediaPlayer.pause();
            mLastPosition = mMediaPlayer.getCurrentPosition();
            Log.d(TAG, "got last position:" + mLastPosition);
        }

        stop();
    }

    private void showMediaControllerBar(final boolean show) {
        Log.d(TAG, "showMediaControllerBar:" + show);
        mPlayerHandler.removeMessages(MessageType.UPDATE.value());
        mPlayerHandler.removeMessages(MessageType.HIDE_CONTROLLER_BAR.value());

        if (show) {
            mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
            mPlayerHandler.sendEmptyMessageDelayed(MessageType.HIDE_CONTROLLER_BAR.value(), mMediaControllerBarVisibleTime);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (show) {
                    mMediaControllerBar.setVisibility(View.VISIBLE);
                } else {
                    mMediaControllerBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void update() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                final int position = mMediaPlayer.getCurrentPosition();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaControllerBar.setCurrentPosition(position);
                    }
                });
                mPlayerHandler.removeMessages(MessageType.UPDATE.value());
                mPlayerHandler.sendEmptyMessageDelayed(MessageType.UPDATE.value(), 500);
            }
        }
    }

    private void updateDisplayRegion() {
        if (mMediaPlayer != null) {
            updateDisplayRegion(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
        }
    }

    private void updateDisplayRegion(final int width, final int height) {
        Log.d(TAG, "updateDisplayRegion, width:" + width + ", height:" + height);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (width <= 0 || height <= 0) {
                    mSurfaceView.getHolder().setSizeFromLayout();
                } else if (width * mDisplayLayout.getMeasuredHeight() > width * mDisplayLayout.getMeasuredWidth()) {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredWidth(), mDisplayLayout.getMeasuredWidth() * height * 100 / width / 100);
                } else {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredHeight() * width * 100 / height / 100, mDisplayLayout.getMeasuredHeight());
                }
            }
        });
    }

    private void changeState(PlayerState state) {
        Log.d(TAG, "state:" + state);
        mState = state;
        mController.changeState(convertState(state));
    }

    private int convertState(PlayerState state) {
        switch (state) {
            case PLAYING: return PlaybackController.STATE_PLAYING;
            case IDLE: return PlaybackController.STATE_IDLE;
            case PREPARING: return PlaybackController.STATE_PREPARING;
            case PAUSED: return PlaybackController.STATE_PAUSED;
            default: throw new IllegalArgumentException();
        }
    }
}