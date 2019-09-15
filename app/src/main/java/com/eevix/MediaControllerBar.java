package com.eevix;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MediaControllerBar extends LinearLayout implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "MediaControllerBar";
    private ImageButton mPlayButton = null;
    private SeekBar mSeekBar = null;
    private TextView mPositionView = null;
    private TextView mDurationView = null;
    private boolean mIsPlaying = false;
    private boolean mIsTouchingSeekBar = false;
    private int mTouchedProgress = 0;
    private OnPlaybackControlListener mListener = null;

    public interface OnPlaybackControlListener {
        void onPlay(boolean play);
        void onSeek(int position);
    }

    public MediaControllerBar(Context context) {
        super(context);
    }

    public MediaControllerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(21)
    public MediaControllerBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @UiThread
    void setListener(OnPlaybackControlListener listener) {
        mListener = listener;
    }

    @UiThread
    void reset() {
        Log.d(TAG, "reset");
        if (!isAttachedToWindow()) {
            return;
        }
        mPlayButton.setImageDrawable(ContextCompat.getDrawable(getContext(), android.R.drawable.ic_media_play));
        mDurationView.setText(R.string.NoTime);
        mPositionView.setText(R.string.NoTime);
        mSeekBar.setProgress(0);
        mSeekBar.setMax(0);
        mIsPlaying = false;
    }

    @UiThread
    void setDuration(int duration) {
        Log.d(TAG, "setDuration:" + duration + "ms");
        if (!isAttachedToWindow()) {
            return;
        }
        if (duration > 0) {
            mDurationView.setText(millisecondsToTime(duration));
            mSeekBar.setMax(duration);
        } else {
            mDurationView.setText(R.string.NoTime);
            mSeekBar.setMax(0);
        }
    }

    @UiThread
    void setCurrentPosition(int position) {
        if (!isAttachedToWindow()) {
            return;
        }
        if (!mIsTouchingSeekBar) {
            mSeekBar.setProgress(position);
        }
    }

    @UiThread
    void setIsPlaying(boolean isPlaying) {
        Log.d(TAG, "setIsPlaying:" + isPlaying);
        if (!isAttachedToWindow()) {
            return;
        }
        mIsPlaying = isPlaying;

        if (mIsPlaying) {
            mPlayButton.setImageDrawable(ContextCompat.getDrawable(getContext(), android.R.drawable.ic_media_pause));
        } else {
            mPlayButton.setImageDrawable(ContextCompat.getDrawable(getContext(), android.R.drawable.ic_media_play));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        Log.d(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        initViews();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mPositionView.setText(millisecondsToTime(progress));
        if (fromUser) {
            mTouchedProgress = progress;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStartTrackingTouch");
        mIsTouchingSeekBar = true;
        mTouchedProgress = mSeekBar.getProgress();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStopTrackingTouch");
        mIsTouchingSeekBar = false;
        if (mListener != null) {
            mListener.onSeek(mTouchedProgress);
        }
    }

    private void initViews() {
        Log.d(TAG, "initViews");
        mPositionView = findViewById(R.id.position);
        mDurationView = findViewById(R.id.duration);
        mSeekBar = findViewById(R.id.seekBar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(0);
        mPlayButton = findViewById(R.id.play);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onPlay(!mIsPlaying);
                }
            }
        });
    }

    private String millisecondsToTime(int milliseconds) {
        return String.format("%02d:%02d:%02d", milliseconds / 1000 / 3600, milliseconds / 1000 / 60 % 60, milliseconds / 1000 % 60);
    }
}
