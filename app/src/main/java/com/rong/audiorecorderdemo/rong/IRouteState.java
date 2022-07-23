package com.rong.audiorecorderdemo.rong;

public interface IRouteState {
    void setSpeakerphoneOn(boolean on);

    void recoverState();

    void cancelState();

    void start();

    void stop();

    RCAudioRouteType getType();
}
