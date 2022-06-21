package com.rong.audiorecorderdemo.rong;

/** 音频路由事件回调 */
public interface IRCRTCAudioRouteListener {

    /**
     * 音频路由设备改变通知
     *
     * @param type 设备类型
     */
    void onRouteChanged(RCAudioRouteType type);

    /**
     * 音频路由切换失败通知
     *
     * @param fromType 原状态
     * @param toType 目标
     */
    void onRouteSwitchFailed(RCAudioRouteType fromType, RCAudioRouteType toType);
}
