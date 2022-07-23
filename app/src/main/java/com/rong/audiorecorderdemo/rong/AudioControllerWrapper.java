package com.rong.audiorecorderdemo.rong;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public class AudioControllerWrapper {
    public static final String ACTION_HEADSET_PLUG = AudioManager.ACTION_HEADSET_PLUG;
    public static final String ACTION_CONNECTION_STATE_CHANGED =
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
    public static final String ACTION_AUDIO_STATE_CHANGED =
            BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED;
    public static final String ACTION_SCO_AUDIO_STATE_UPDATED =
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED;
    public static final String ACTION_STATE_CHANGED = BluetoothAdapter.ACTION_STATE_CHANGED;
    public static final String ACTION_BTADAPTER_CONNECTION_STATE_CHANGED =
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED;

    private static final String TAG = "AudioControllerWrapper";

    AudioManager mAudioManager;
    Context context;
    private int preMode = -1;

    @SuppressLint("WrongConstant")
    public AudioControllerWrapper(Context context) {
        if (null == context) {
            return;
        }
        this.context = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.setMode(AudioManager.MODE_CURRENT);
        preMode = mAudioManager.getMode();
    }

    public void setSpeakerphoneOn(boolean on) {
        if (null != mAudioManager) {
            mAudioManager.setSpeakerphoneOn(on);
        }
    }

    public boolean isSpeakerphoneOn() {
        if (null != mAudioManager) {
            mAudioManager.isSpeakerphoneOn();
        }
        return true;
    }

    public int getMode() {
        if (null != mAudioManager) {
            mAudioManager.getMode();
        }

        return -1;
    }

    public boolean getScoOn() {
        if (null != mAudioManager) {
            return mAudioManager.isBluetoothScoOn();
        }

        return false;
    }

    public boolean getA2dpOn() {
        if (null != mAudioManager) {
            return mAudioManager.isBluetoothA2dpOn();
        }

        return false;
    }

    public boolean getAvailable() {
        if (null != mAudioManager) {
            return mAudioManager.isBluetoothScoAvailableOffCall();
        }

        return false;
    }

    public boolean isWiredHeadsetOn() {
        if (null != mAudioManager) {
            return mAudioManager.isWiredHeadsetOn();
        }

        return false;
    }

    public boolean isBluetoothHeadSet() {
        BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            int profileConnectionState =
                    bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
            return profileConnectionState == BluetoothProfile.STATE_CONNECTED;
        }
        return false;
    }

    @SuppressLint("WrongConstant")
    public void setSco(boolean on, boolean isRetry, boolean needClose) {
        Log.d(TAG, "setSco on:" + on + " isRetry:"+isRetry + " needClose: " + needClose);
        if (null != mAudioManager) {
            if (on) {
                if (!isRetry) {
                    preMode = mAudioManager.getMode();
                }
                if (needClose) {
                    mAudioManager.stopBluetoothSco();
                }
                // 蓝牙模式强制使用 MODE_IN_COMMUNICATION 模式
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mAudioManager.startBluetoothSco();
                mAudioManager.setBluetoothScoOn(true);
            } else {
                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);
                mAudioManager.setMode(preMode);
            }
        }
    }

    public void setSco(boolean on, boolean isRetry) {
        setSco(on, isRetry, true);
    }

    public BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getAdapter();
    }
}
