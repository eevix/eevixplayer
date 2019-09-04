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
    private SurfaceView     mSurfaceView;
    private View            mDisplayLayout;
    private MediaPlayer     mMediaPlayer;
    private Handler         mMainHandler;
    private Handler         mPlayerHandler;
    private HandlerThread   mPlayerHandlerThread;
    private MediaPlayerListener mMediaPlayerListener;
    private MediaControllerBar mMediaControllerBar;
    private boolean         mSurfaceValid = false;
    private int             mVideoWidth = 0;
    private int             mVideoHeight = 0;
    private DLNAMediaRender.PlaybackControllerRegister mPlaybackControllerRegister;
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
            mPlayerHandler.removeMessages(MessageType.UPDATE.value());
            mPlayerHandler.obtainMessage(MessageType.SEEK.value(), millisecond, 0).sendToTarget();
            mMainHandler.removeMessages(MessageType.UPDATE.value());
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
            if (mStateChangedListener != null) {
                mStateChangedListener.onStateChanged(state);
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
            mPlayerHandler.sendEmptyMessage(MessageType.PREPARED.value());
        }

        @Override
        public void onSeekComplete(MediaPlayer mp) {
            Log.d(TAG, "MediaPlayerListener: onSeekComplete");
            mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
            mPlayerHandler.sendEmptyMessage(MessageType.START.value());
        }

        @Override
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            Log.d(TAG, "MediaPlayerListener: onVideoSizeChanged, width:" + width + ", height:" + height);
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
        mMediaPlayerListener = new MediaPlayerListener();
        mPlayerHandlerThread = new HandlerThread("playerThread");
        mMainHandler.sendEmptyMessageDelayed(MessageType.HIDE_CONTROLLER_BAR.value(), mMediaControllerBarVisibleTime);

        mPlayerHandlerThread.start();
        mPlayerHandler = new Handler(mPlayerHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handlePlayerThreadMessage(msg);
            }
        };
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
        onIntent(getIntent());
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        if (mPlaybackControllerRegister != null) {
            mPlaybackControllerRegister.registerPlayerBackController(null);
            mPlaybackControllerRegister = null;
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
        Log.d(TAG, "onIntent:" + intent);
        String action = intent.getAction();
        String data = intent.getDataString();
        String from = intent.getStringExtra("from");

        if (from !=null && from.equals("DLNAMediaRender")) {
            Intent intent2 = new Intent();
            intent2.setClass(this, DLNAMediaRender.class);
            bindService(intent2, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "onServiceConnected, name:" + name + ", service:" + service);
                    if (service instanceof DLNAMediaRender.PlaybackControllerRegister) {
                        mPlaybackControllerRegister = (DLNAMediaRender.PlaybackControllerRegister) service;
                        mPlaybackControllerRegister.registerPlayerBackController(mController);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "onServiceDisconnected, name:" + name);
                    mController.stop();
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    Log.d(TAG, "onBindingDied, name:" + name);
                    mController.stop();
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    Log.d(TAG, "onNullBinding, name:" + name);
                }
            }, BIND_AUTO_CREATE);
        }

        if (action != null && action.equals(Intent.ACTION_VIEW)) {
            mController.setDataSource(data);
        }
    }

    private boolean handleMainThreadMessage(Message msg) {
        MessageType message = MessageType.valueOf(msg.what);
        if (message == null) {
            return false;
        }

        if (message != MessageType.UPDATE) {
            Log.d(TAG, "handleMainThreadMessage:" + message);
        }

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

                Log.d(TAG, "mVideoWidth:" + mVideoWidth + ", mVideoHeight:" + mVideoHeight);

                if (mVideoWidth <= 0 || mVideoHeight <= 0) {
                    mSurfaceView.getHolder().setSizeFromLayout();
                } else if (mVideoWidth * mDisplayLayout.getMeasuredHeight() > mVideoHeight * mDisplayLayout.getMeasuredWidth()) {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredWidth(), mDisplayLayout.getMeasuredWidth() * mVideoHeight * 100 / mVideoWidth / 100);
                } else {
                    mSurfaceView.getHolder().setFixedSize(mDisplayLayout.getMeasuredHeight() * mVideoWidth * 100 / mVideoHeight / 100, mDisplayLayout.getMeasuredHeight());
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
        MessageType message = MessageType.valueOf(msg.what);
        if (message == null) {
            return;
        }

        if (message != MessageType.UPDATE
            && message != MessageType.GET_CURRENT_POSITION
            && message != MessageType.QUERY_STATE) {
            Log.d(TAG, "handlePlayerThreadMessage:" + message);
        }

        switch (message) {
            case SET_DATA_SOURCE: {
                if (msg.obj instanceof String) {
                    play((String)msg.obj);
                }
                break;
            }
            case START: {
                if (mMediaPlayer != null && mState == PlayerState.PAUSED) {
                    try {
                        mMediaPlayer.start();
                        mMainHandler.sendEmptyMessage(MessageType.STARTED.value());
                        mPlayerHandler.sendEmptyMessage(MessageType.UPDATE.value());
                        changeState(PlayerState.PLAYING);
                    } catch (Exception exception) {
                        Log.e(TAG, "exception:", exception);
                    }
                } else if (mUrl != null && mState == PlayerState.IDLE) {
                    play(mUrl);
                }
                break;
            }
            case PAUSE: {
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.pause();
                        mMainHandler.sendEmptyMessage(MessageType.PAUSED.value());
                        changeState(PlayerState.PAUSED);
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
                    if (mMediaPlayer != null && (mState == PlayerState.PLAYING || mState == PlayerState.PAUSED)) {
                        mMediaPlayer.seekTo(msg.arg1);
                    }
                } catch (Exception exception) {
                    Log.e(TAG, "exception:", exception);
                }
                break;
            }
            case GET_CURRENT_POSITION: {
                int position = 0;
                if (mMediaPlayer != null) {
                    position = mMediaPlayer.getCurrentPosition();
                }

                MessageReply<Integer> reply = ((MessageReply<Integer>) msg.obj);
                reply.setData(position);
                reply.notifyReply();
                break;
            }
            case GET_DURATION: {
                int duration = 0;
                if (mMediaPlayer != null) {
                    duration = mMediaPlayer.getDuration();
                }

                MessageReply<Integer> reply = ((MessageReply<Integer>) msg.obj);
                reply.setData(duration);
                Log.d(TAG, "GET_DURATION duration:" + duration);
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
                try {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.start();
                        Log.e(TAG, "mMediaPlayer.start()");
                        changeState(PlayerState.PLAYING);
                        Message message1 = mMainHandler.obtainMessage(MessageType.STARTED.value());
                        Bundle bundle = new Bundle();
                        bundle.putInt("duration", mMediaPlayer.getDuration());
                        bundle.putInt("video-width", mMediaPlayer.getVideoWidth());
                        bundle.putInt("video-height", mMediaPlayer.getVideoHeight());
                        message1.setData(bundle);
                        message1.sendToTarget();
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
                mSurfaceValid = true;
                if (mMediaPlayer != null) {
                    Log.e(TAG, "mMediaPlayer.setDisplay()");
                    mMediaPlayer.setDisplay(mSurfaceView.getHolder());
                }
                break;
            }
            case SURFACE_DESTROYED: {
                mSurfaceValid = false;
                stop();
                mMainHandler.removeMessages(MessageType.UPDATE.value());
                break;
            }
            case UPDATE: {
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        Message message1 = mMainHandler.obtainMessage(MessageType.UPDATE.value());
                        Bundle bundle = new Bundle();
                        bundle.putInt("currentTime", mMediaPlayer.getCurrentPosition());
                        message1.setData(bundle);
                        message1.sendToTarget();
                        mPlayerHandler.removeMessages(MessageType.UPDATE.value());
                        mPlayerHandler.sendEmptyMessageDelayed(MessageType.UPDATE.value(), 500);
                    }
                }
                break;
            }
            case UPDATE_VIDEO_SIZE: {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    Message message1 = mMainHandler.obtainMessage(MessageType.UPDATE_VIDEO_SIZE.value());
                    Bundle bundle = new Bundle();
                    bundle.putInt("video-width", mMediaPlayer.getVideoWidth());
                    bundle.putInt("video-height", mMediaPlayer.getVideoHeight());
                    message1.setData(bundle);
                    message1.sendToTarget();
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
        mMediaPlayer.setOnPreparedListener(mMediaPlayerListener);
        mMediaPlayer.setOnCompletionListener(mMediaPlayerListener);
        mMediaPlayer.setOnSeekCompleteListener(mMediaPlayerListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mMediaPlayerListener);
        mMediaPlayer.setScreenOnWhilePlaying(true);

        try {
            Log.e(TAG, "mMediaPlayer.setDataSource()");
            mMediaPlayer.setDataSource(path);
            if (mSurfaceValid) {
                Log.e(TAG, "mMediaPlayer.setDisplay()");
                mMediaPlayer.setDisplay(mSurfaceView.getHolder());
            }
            Log.e(TAG, "mMediaPlayer.prepareAsync()");
            mMediaPlayer.prepareAsync();
            changeState(PlayerState.PREPARING);
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

        changeState(PlayerState.IDLE);
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