package com.rong.audiorecorderdemo.rong;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;


import com.rong.audiorecorderdemo.rtc.ThreadUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;


public class RTCAudioRouteManagerImpl extends RCRTCAudioRouteManager {
    private static final String TAG = "AudioRouteManager";

    IRCRTCAudioRouteListener mAudioRouteListener;
    RCAudioRouteChangeListener mInternalListener;
    AudioControllerWrapper mAudioControllerWrapper;
    BroadcastReceiver mHeadsetReceiver;
    BroadcastReceiver mBtHeadsetReceiver;
    Context mContext;
    StateManager mStateManager;
    IRouteState mCurrentState;
    IRouteState mIdleState = new IdleState();
    IRouteState mHeadSetState = new HeadSetState();
    IRouteState mBtHeadSetState = new BtHeadSetState();
    boolean mUserSpeakerSet = true;
    private boolean initFlag = false;
    private RCBluetoothEventsManager bluetoothEventsManager;

    private static volatile RTCAudioRouteManagerImpl INSTANCE = null;

    public static RTCAudioRouteManagerImpl getInstance() {
        if (INSTANCE == null) {
            synchronized (RTCAudioRouteManagerImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RTCAudioRouteManagerImpl();
                }
            }
        }
        return INSTANCE;
    }

    private RTCAudioRouteManagerImpl() {
        Looper handle;
        if ((handle = Looper.myLooper()) != null) {
            this.mHandler = new Handler(handle);
        } else if ((handle = Looper.getMainLooper()) != null) {
            this.mHandler = new Handler(handle);
        } else {
            this.mHandler = null;
            Log.d("AudioRouteManager", "handler is null");
            return;
        }
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    class IdleState implements IRouteState {
        @Override
        public void setSpeakerphoneOn(boolean on) {
            if (null != mAudioControllerWrapper) {
                Log.d("AudioRouteManager",
                        "IdleState:" + on);
                mAudioControllerWrapper.setSpeakerphoneOn(on);
                onAudioRouteChanged(
                        on ? RCAudioRouteType.SPEAKER_PHONE : RCAudioRouteType.EARPIECE);
            }
        }

        @Override
        public void recoverState() {
            if (null != mAudioControllerWrapper) {
                Log.d("AudioRouteManager",
                        "recoverState");
                if (mHandler != null) {
                    // FIXME: 2021/8/20 三星某些型号手机（如 A71），拔掉耳机之后立刻调用该方法不会生效，需适当延迟
                    mHandler.postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mAudioControllerWrapper.setSpeakerphoneOn(mUserSpeakerSet);
                                }
                            },
                            50);
                }
            }
        }

        @Override
        public void cancelState() {
            Log.d("AudioRouteManager",  "cancelState");
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public RCAudioRouteType getType() {
            if (mUserSpeakerSet) {
                return RCAudioRouteType.SPEAKER_PHONE;
            } else {
                return RCAudioRouteType.EARPIECE;
            }
        }
    }

    class HeadSetState implements IRouteState {

        @Override
        public void setSpeakerphoneOn(boolean on) {
            Log.d("AudioRouteManager",
                    "setSpeakerphoneOn: " + on);
        }

        @Override
        public void recoverState() {
            if (null != mAudioControllerWrapper) {
                Log.d("AudioRouteManager",
                        "recoverState");
                mAudioControllerWrapper.setSpeakerphoneOn(false);
            }
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void cancelState() {
            Log.d("AudioRouteManager","cancelState HeadSetState");
        }

        @Override
        public RCAudioRouteType getType() {
            return RCAudioRouteType.HEADSET;
        }
    }

    class BtHeadSetState implements IRouteState {

        @Override
        public void setSpeakerphoneOn(boolean on) {
            Log.d("",
                    "setSpeakerphoneOn BtHeadSetState");
        }

        @Override
        public void recoverState() {
            Log.d("AudioRouteManager",
                    "recoverState BtHeadSetState");
//            if (null != mAudioControllerWrapper) {
//                mAudioControllerWrapper.setSco(true, false);
//                mAudioControllerWrapper.setSpeakerphoneOn(false);
//            }
        }

        @Override
        public void start() {
            if (mAudioControllerWrapper != null){
                mAudioControllerWrapper.setSco(true, false, false);
            }
        }

        @Override
        public void stop() {
            if (mAudioControllerWrapper != null){
                mAudioControllerWrapper.setSco(false, false, false);
            }
        }

        @Override
        public void cancelState() {
            Log.d("AudioRouteManager",
                    "cancelState BtHeadSetState");
            if (null != mAudioControllerWrapper) {
                mAudioControllerWrapper.setSco(false, false);
            }
        }

        @Override
        public RCAudioRouteType getType() {
            return RCAudioRouteType.HEADSET_BLUETOOTH;
        }
    }

    private class BluetoothEvents implements RCBluetoothEventsManager.BluetoothEvents{

        @Override
        public void onDeviceConnected() {
            mStateManager.removeAndOffer(mBtHeadSetState);
            stateOperation();
        }

        @Override
        public void onDeviceDisconnected() {
            mStateManager.removeAndOffer(mIdleState);
            stateOperation();
        }

        @Override
        public void onStartBlueTooth() {
            if (mCurrentState == mBtHeadSetState){
                mCurrentState.start();
            }
        }

        @Override
        public void onStopBlueTooth() {
            if (mCurrentState == mBtHeadSetState){
                mCurrentState.stop();
            }
        }

        @Override
        public void onBlueToothStarted() {

        }

        @Override
        public void onBlueToothStopped() {

        }

        @Override
        public void onBlueToothStartFailed(RCBluetoothEventsManager.BluetoothError error) {

        }
    }

    private class HeadsetBroadcastReceiver extends BroadcastReceiver {

        private void internaProcess(Context context, Intent intent) {
            if (AudioControllerWrapper.ACTION_HEADSET_PLUG.equalsIgnoreCase(intent.getAction())
                    && intent.hasExtra("state")) {
                int state = intent.getIntExtra("state", -1);
                if (state == 1) {
                    int microphone = intent.getIntExtra("microphone", -1);

                    Log.d("AudioRouteManager",
                            " removeAndOffer" +
                            state + microphone);
                    mStateManager.removeAndOffer(mHeadSetState);
                } else if (state == 0) {
                    Log.d("AudioRouteManager",
                            "remove connectionstate"+ state);
                    mStateManager.remove(mHeadSetState);
                } else {
                    Log.d("AudioRouteManager",
                            "HeadsetBroadcastReceiver connectionstate" + state);
                }
            }
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (null == mHandler) {
                return;
            }
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            internaProcess(context, intent);
                            stateOperation();
                        }
                    });
        }
    }

    private class BtHeadsetBroadcastReceiver extends BroadcastReceiver {

        // 启动尝试间隔,1秒
        private static final long RETRY_OPEN_SCO_DURING = 2000L;
        // 蓝牙启动尝试次数
        private static final int MAX_RETRY_OPEN_SCO_TIMES = 5;
        public static final String TOKEN_RETRY_OPEN_SCO = "TOKEN_RETRY_OPEN_SCO";
        private final AtomicInteger retryOpenScoTimes = new AtomicInteger(MAX_RETRY_OPEN_SCO_TIMES);

        private void internalProcess(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "internaProcess : " + "action = " + action);
            if (null == action) {
                return;
            }
            if (!AudioControllerWrapper.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)
                    && !AudioControllerWrapper.ACTION_AUDIO_STATE_CHANGED.equals(action)) {
                retryOpenScoTimes.set(MAX_RETRY_OPEN_SCO_TIMES);
                Handler handler = mHandler;
                if (handler != null) {
                    handler.removeCallbacksAndMessages(TOKEN_RETRY_OPEN_SCO);
                }
            }
            int curState = 0;
            int preState = 0;
            BluetoothDevice bluetoothDevice;
            // 蓝牙连接状态改变
            if (action.equalsIgnoreCase(AudioControllerWrapper.ACTION_CONNECTION_STATE_CHANGED)) {
                curState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -99);
                preState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -99);

                if (BluetoothProfile.STATE_CONNECTED == curState
                        && mAudioControllerWrapper.isBluetoothHeadSet()) {
                    mStateManager.removeAndOffer(mBtHeadSetState);
                    // 无论开启成功与否，3 秒之后都再次设置一次蓝牙状态，部分设备上存在首次设置 sco 模式显示成功，但实际为成功的问题
                    // 该逻辑不会对原有逻辑产生影响
                    Handler handler = RTCAudioRouteManagerImpl.this.mHandler;
                    if (handler != null && handler.getLooper().getThread().isAlive()) {
                        handler.postDelayed(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mCurrentState == mBtHeadSetState && hasInit()) {
                                            mAudioControllerWrapper.setSco(true, true, false);
                                        }
                                    }
                                },
                                3000);
                    }
                    Log.d("AudioRouteManager",
                            "curconnectionstate " +curState + "removeAndOffer "+
                            preState);

                } else if (BluetoothProfile.STATE_DISCONNECTED == curState) {
                    mStateManager.remove(mBtHeadSetState);
                    Log.d("AudioRouteManager", "curState : " + curState);
                }

            } else if (action.equalsIgnoreCase(AudioControllerWrapper.ACTION_AUDIO_STATE_CHANGED)) {
                // A2DP 音频状态改变通知
                // do nothing
            } else if (action.equalsIgnoreCase(
                    AudioControllerWrapper.ACTION_SCO_AUDIO_STATE_UPDATED)) {
                curState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -99);
                preState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, -99);
                // Sco 模式开启失败
                if (mCurrentState == mBtHeadSetState
                        && AudioManager.SCO_AUDIO_STATE_CONNECTED != curState) {
                    Log.d("AudioRouteManager",
                            "Sco reconnect" + curState);
                    Handler handler = mHandler;
                    boolean hasMessage = false;
                    if (handler != null) {
                        hasMessage = handler.hasMessages(0, TOKEN_RETRY_OPEN_SCO);
                    }

                    final int times;
                    if (hasMessage) {
                        handler.removeCallbacksAndMessages(TOKEN_RETRY_OPEN_SCO);
                        times = retryOpenScoTimes.get() + 1;
                    } else {
                        times = retryOpenScoTimes.getAndDecrement();
                    }
                    if (times > 0) {
                        // 每秒尝试一次，最多尝试
                        if (handler != null) {
                            handler.postAtTime(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mCurrentState == mBtHeadSetState) {
                                                Log.d("AudioRouteManager",
                                                        "times" +
                                                        times);
                                                mAudioControllerWrapper.setSco(true, true);
                                            }
                                        }
                                    },
                                    TOKEN_RETRY_OPEN_SCO,
                                    SystemClock.uptimeMillis()
                                            + (times == MAX_RETRY_OPEN_SCO_TIMES
                                                    ? 0
                                                    : RETRY_OPEN_SCO_DURING));
                        }
                    } else {
                        // 5 次都失败，则移除蓝牙态
                        mStateManager.remove(mBtHeadSetState);
                        Log.e("AudioRouteManager", "desc open sco failed");

                        IRCRTCAudioRouteListener audioRouteListener = mAudioRouteListener;
                        if (audioRouteListener != null) {
                            IRouteState fromState = mStateManager.peekLast();
                            if (fromState != null) {
                                Log.d("AudioRouteManager",
                                        "fromType" +
                                        fromState.getType() + " state type " +
                                        mBtHeadSetState.getType());
                                audioRouteListener.onRouteSwitchFailed(
                                        fromState.getType(), mBtHeadSetState.getType());
                            }
                        }
                    }
                } else if (AudioManager.SCO_AUDIO_STATE_CONNECTED == curState
                        && mAudioControllerWrapper.getScoOn()) {
                    Log.d("AudioRouteManager","desc sco start success");
                    // 一旦连接成功，立刻清楚所有重连 task
                    Handler handler = mHandler;
                    if (handler != null) {
                        handler.removeCallbacksAndMessages(TOKEN_RETRY_OPEN_SCO);
                    }
                }
            } else if (action.equalsIgnoreCase(AudioControllerWrapper.ACTION_STATE_CHANGED)) {
                // 蓝牙适配器状态改变
                // 某些设备本事不支持蓝牙通话，这种情况下断开只走这个 case
                AudioControllerWrapper audioController =
                        RTCAudioRouteManagerImpl.this.mAudioControllerWrapper;
                if (audioController == null) {
                    return;
                }
                int bluetoothConnectionState =
                        audioController
                                .getBluetoothAdapter()
                                .getProfileConnectionState(BluetoothProfile.HEADSET);
                if (bluetoothConnectionState == BluetoothProfile.STATE_DISCONNECTED) {
                    mStateManager.remove(mBtHeadSetState);
                }

            } else if (action.equalsIgnoreCase(
                    AudioControllerWrapper.ACTION_BTADAPTER_CONNECTION_STATE_CHANGED)) {
                // 蓝牙配置信息发生改变
                curState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -99);
                preState =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, -99);

                if (BluetoothAdapter.STATE_CONNECTED == curState
                        && mAudioControllerWrapper.isBluetoothHeadSet()) {
                    mStateManager.removeAndOffer(mBtHeadSetState);
                    bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    BluetoothClass bluetoothClass = bluetoothDevice.getBluetoothClass();
                    boolean hasAudioService =
                            bluetoothClass.hasService(BluetoothClass.Service.AUDIO);
                    boolean hasTelService =
                            bluetoothClass.hasService(BluetoothClass.Service.TELEPHONY);
                    Log.d("AudioRouteManager",
                            "curState:" + curState);

                } else if (BluetoothAdapter.STATE_DISCONNECTED == curState) {
                    mStateManager.remove(mBtHeadSetState);
                    Log.d("AudioRouteManager",
                            "curState" +
                            preState);
                }
            }
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (mHandler == null) {
                return;
            }
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            internalProcess(context, intent);
                            stateOperation();
                        }
                    });
        }
    }

    Handler mHandler = null;
    Handler mainThreadHandler = null;

    @Override
    public void init(final Context context) {
//        if (!context.getApplicationInfo()
//                .packageName
//                .equals(getCurrentProcessName(context.getApplicationContext()))) {
//           Log.d("AudioRouteManager"," ipc not allow init");
//            return;
//        }

        mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        if (initFlag) {
                            // 防止重复初始化
                            Log.d("AudioRouteManager","init already");
                            return;
                        }
                        initFlag = true;

                        Log.d("AudioRouteManager", "init" + context == null ? "null" : "");
                        if (context == null) {
                            throw new IllegalArgumentException(
                                    "The context not allowed to be null");
                        }

//                        RCMicOutputStreamImpl stream =
//                                ((RCMicOutputStreamImpl)
//                                        RCRTCEngine.getInstance().getDefaultAudioStream());
//                        if (stream != null) {
//                            mInternalListener = stream.getAudioRouteChangeListener();
//                        } else {
//                            FinLog.w(
//                                    TAG,
//                                    "RCRTCEngine.getInstance().getDefaultAudioStream() is null for"
//                                            + " login failed");
//                        }

                        mContext = context;
                        mAudioControllerWrapper = new AudioControllerWrapper(mContext);
                        mStateManager = new StateManager();
                        mStateManager.add(mIdleState);
                        mHeadsetReceiver = new HeadsetBroadcastReceiver();
//                        mBtHeadsetReceiver = new BtHeadsetBroadcastReceiver();
                        mContext.registerReceiver(
                                mHeadsetReceiver,
                                new IntentFilter(AudioControllerWrapper.ACTION_HEADSET_PLUG));
                        bluetoothEventsManager = RCBluetoothEventsManager.create(context);
                        bluetoothEventsManager.setEvents(new BluetoothEvents());
                        bluetoothEventsManager.start();

//                        IntentFilter btIntentFilter = new IntentFilter();
//                        btIntentFilter.addAction(
//                                AudioControllerWrapper.ACTION_CONNECTION_STATE_CHANGED);
//                        btIntentFilter.addAction(AudioControllerWrapper.ACTION_AUDIO_STATE_CHANGED);
//                        btIntentFilter.addAction(
//                                AudioControllerWrapper.ACTION_SCO_AUDIO_STATE_UPDATED);
//                        btIntentFilter.addAction(AudioControllerWrapper.ACTION_STATE_CHANGED);
//                        btIntentFilter.addAction(
//                                AudioControllerWrapper.ACTION_BTADAPTER_CONNECTION_STATE_CHANGED);
//                        mContext.registerReceiver(mBtHeadsetReceiver, btIntentFilter);
                        try {
                            resetAudioRouteStateInternal();
                        } catch (Exception e) {
                            Log.d("AudioRouteManager","has been unInit");
                            e.printStackTrace();
                        }
                        Log.d("AudioRouteManager", "init success");
                    }
                });
    }

    @Override
    public void resetAudioRouteState() {
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
            resetAudioRouteStateInternal();
        } else {
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            resetAudioRouteStateInternal();
                        }
                    });
        }
    }

    private void resetAudioRouteStateInternal() {
        Log.d("AudioRouteManager", "resetAudioRouteStateInternal");
        checkInit();
        mStateManager.remove(mHeadSetState);
        mStateManager.remove(mBtHeadSetState);
        mStateManager.removeAndOffer(mIdleState);
        if (mAudioControllerWrapper.isWiredHeadsetOn()) {
            Log.d("AudioRouteManager", "isWiredHeadsetOn");
            mStateManager.removeAndOffer(mHeadSetState);
        }
        if (mAudioControllerWrapper.isBluetoothHeadSet()) {
            Log.d("AudioRouteManager", "hasBluetooth");
            mStateManager.removeAndOffer(mBtHeadSetState);
        }
        mUserSpeakerSet = mAudioControllerWrapper.isSpeakerphoneOn();
        Log.d("AudioRouteManager", "speakerOn " +mUserSpeakerSet);
        stateOperation();
        Log.d("AudioRouteManager", "reset finish");
    }

    @Override
    public boolean hasHeadSet() {
        return ThreadUtils.invokeAtFrontUninterruptibly(
                mHandler,
                new Callable<Boolean>() {

                    @Override
                    public Boolean call() throws Exception {
                        checkInit();
                        boolean wiredHeadsetOn =
                                mAudioControllerWrapper.mAudioManager.isWiredHeadsetOn();
                        Log.d("AudioRouteManager", "hasHeadSet "+ wiredHeadsetOn);
                        return wiredHeadsetOn;
                    }
                });
    }

    @Override
    public boolean hasBluetoothA2dpConnected() {
        boolean bool = false;
        AudioControllerWrapper audioControllerWrapper = this.mAudioControllerWrapper;
        if (audioControllerWrapper == null) {
            return false;
        }
        BluetoothAdapter mAdapter = audioControllerWrapper.getBluetoothAdapter();
        if (mAdapter != null && mAdapter.isEnabled()) {
            int a2dp = mAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
            if (a2dp == BluetoothProfile.STATE_CONNECTED) {
                bool = true;
            }
        }
        Log.d("AudioRouteManager", "hasBluetooth " + bool);
        return bool;
    }

    private void stateOperation() {
        IRouteState tmpState = mStateManager.peekLast();
        if (mCurrentState != null && tmpState != mCurrentState) {
            mCurrentState.cancelState();
            mCurrentState = tmpState;
            mCurrentState.recoverState();
            onAudioRouteChanged(mCurrentState.getType());
        } else if (mCurrentState == null) {
            mCurrentState = mIdleState;
            onAudioRouteChanged(mCurrentState.getType());
        }
    }

    private void onAudioRouteChanged(final RCAudioRouteType type) {
        Log.d("AudioRouteManager", "routeType :"+ type);
        final IRCRTCAudioRouteListener listener = this.mAudioRouteListener;
        if (listener != null && mainThreadHandler != null) {
            mainThreadHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onRouteChanged(type);
                        }
                    });
        }
//        if (mInternalListener != null) {
//            mInternalListener.onAudioRouteChanged(type);
//        }
    }

    /**
     * 设置输出设备切换监听
     *
     * @param listener
     */
    @Override
    public void setOnAudioRouteChangedListener(final IRCRTCAudioRouteListener listener) {
        Log.d("AudioRouteManager", "listener" + listener == null ? "null" : "");
        this.mAudioRouteListener = listener;
        if (listener != null && mainThreadHandler != null) {
            mainThreadHandler.removeCallbacksAndMessages(this);
            mainThreadHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (null != mCurrentState) {
                                listener.onRouteChanged(mCurrentState.getType());
                            }
                        }
                    });
        }
    }

    @Override
    public boolean hasInit() {
        return initFlag;
    }

    @Override
    public void unInit() {
        mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!initFlag) {
                            Log.d("AudioRouteManager", "task has not init");
                            return;
                        }
                        initFlag = false;
                        Log.d("AudioRouteManager", "task unInit");
                        if (mCurrentState != null) {
                            mCurrentState.cancelState();
                        }

                        mContext.unregisterReceiver(mHeadsetReceiver);
//                        mContext.unregisterReceiver(mBtHeadsetReceiver);
                        if (mainThreadHandler != null) {
                            mainThreadHandler.removeCallbacksAndMessages(this);
                        }

                        if (bluetoothEventsManager != null){
                            bluetoothEventsManager.stop();
                            bluetoothEventsManager = null;
                        }

                        mAudioRouteListener = null;
                        mCurrentState = null;
                        mContext = null;
                        mHandler.removeCallbacksAndMessages(
                                BtHeadsetBroadcastReceiver.TOKEN_RETRY_OPEN_SCO);
                        mInternalListener = null;
                    }
                });
    }

    public void setSpeakerphoneOn(final boolean on) {
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
            setSpeakerphoneOnInternal(on);
        } else {
            mHandler.post(
                    new Runnable() {

                        @Override
                        public void run() {
                            setSpeakerphoneOnInternal(on);
                        }
                    });
        }
    }

    private void setSpeakerphoneOnInternal(boolean on) {
        Log.d("AudioRouteManager", "speakerOn "+ on);
        checkInit();
        mUserSpeakerSet = on;
        if (mCurrentState != null) {
            mCurrentState.setSpeakerphoneOn(on);
        }
    }

    private void checkInit() {
        if (!initFlag) {
            Log.d("AudioRouteManager", "desc not init");
            throw new RuntimeException("the audio route manager not init");
        }
    }
}
