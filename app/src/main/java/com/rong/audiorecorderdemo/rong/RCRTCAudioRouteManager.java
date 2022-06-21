package com.rong.audiorecorderdemo.rong;

import android.content.Context;

/** @author gusd @Date 2021/08/23 */
public abstract class RCRTCAudioRouteManager {

    public static RCRTCAudioRouteManager getInstance() {
        return RTCAudioRouteManagerImpl.getInstance();
    }

    /**
     * 初始化音频路由管理类
     *
     * @param context
     */
    public abstract void init(Context context);

    /** 根据当前设备状态重置音频路由状态 */
    public abstract void resetAudioRouteState();

    /**
     * 是否插有线耳机
     *
     * @return
     */
    public abstract boolean hasHeadSet();

    /**
     * 是否连接蓝牙耳机
     *
     * @return
     */
    public abstract boolean hasBluetoothA2dpConnected();

    /**
     * 设置耳机状态改变监听
     *
     * @param listener
     */
    public abstract void setOnAudioRouteChangedListener(IRCRTCAudioRouteListener listener);

    /**
     * 是否初始化
     *
     * @return
     */
    public abstract boolean hasInit();

    /** 反初始化,释放相关资源 */
    public abstract void unInit();
}
