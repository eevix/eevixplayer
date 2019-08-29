package com.eevix;

public interface PlaybackController {
    int STATE_IDLE = 0;
    int STATE_PREPARING = 1;
    int STATE_PAUSED = 2;
    int STATE_PLAYING = 3;
    interface StateChangedListener {
        void onStateChanged(int state);
    }
    void setDataSource(String url);
    void start();
    void pause();
    void resume();
    void stop();
    void seek(int millisecond);
    int getCurrentPosition();
    int getDuration();
    boolean isPlaying();
    int getState();
    void setStateChangedListener(StateChangedListener listener);
}
