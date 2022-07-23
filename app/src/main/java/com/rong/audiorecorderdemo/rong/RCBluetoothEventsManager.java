package com.rong.audiorecorderdemo.rong;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.rong.audiorecorderdemo.rtc.ThreadUtils;

import java.util.List;
import java.util.Set;

public class RCBluetoothEventsManager {

    private static final int ATTEMPT_CONNECT_COUNT = 2;
    private static final int BLUETOOTH_SCO_TIMEOUT_MS = 4000;
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHeadset bluetoothHeadset;
    private BluetoothDevice bluetoothDevice;
    private BroadcastReceiver broadcastReceiver;
    private BluetoothProfile.ServiceListener serviceListener;
    private int scoConnectAttempts = 0;
    private Handler handler;
    private BluetoothEvents events;
    private Runnable timeRunnable;

    private static final String TAG = "RCBluetoothManager";

    public enum BluetoothError{
        NOT_SUPPORT, START_TIMEOUT, NO_PERMISSION
    }

    public interface BluetoothEvents{
        /** 蓝牙设备链接成功 */
        void onDeviceConnected();
        /** 蓝牙设备断开链接 */
        void onDeviceDisconnected();
        /** 通知开启蓝牙事件　*/
        void onStartBlueTooth();
        /** 通知关闭蓝牙事件 */
        void onStopBlueTooth();
        /** 蓝牙开启成功 */
        void onBlueToothStarted();
        /** 蓝牙关闭 **/
        void onBlueToothStopped();
        void onBlueToothStartFailed(BluetoothError error);
    }

    public class BluetoothServiceListener implements BluetoothProfile.ServiceListener{

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "onServiceConnected: profile - " + profile);
            RCBluetoothEventsManager.this.bluetoothHeadset = (BluetoothHeadset) proxy;
            if (RCBluetoothEventsManager.this.events != null){
                RCBluetoothEventsManager.this.events.onDeviceConnected();
            }
            updateDevice();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (RCBluetoothEventsManager.this.events !=null){
                RCBluetoothEventsManager.this.events.onDeviceDisconnected();
            }
        }
    }

    public class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)){
                final int state =
                        intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                final int preState = intent.getIntExtra(BluetoothHeadset.EXTRA_PREVIOUS_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                        + "a=ACTION_CONNECTION_STATE_CHANGED, "
                        + "s=" + stateToString(state) + ", "
                        + "preS=" + stateToString(preState)+ ", "
                        + "sb=" + isInitialStickyBroadcast());
                if (state == BluetoothHeadset.STATE_CONNECTED){
                    updateDevice();
                }else if (state == BluetoothHeadset.STATE_CONNECTING) {
                    if (RCBluetoothEventsManager.this.events != null){
                        RCBluetoothEventsManager.this.events.onDeviceConnected();
                    }
                    // No action needed.
                } else if (state == BluetoothHeadset.STATE_DISCONNECTING) {
                    // no action
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // Bluetooth is probably powered off during the call.
                    // TODO: 2022/6/29 disconnect event for pre state

                    if (events != null){
                        events.onDeviceDisconnected();
                    }
                }
                } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)){
                final int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                final int preState = intent.getIntExtra(BluetoothHeadset.EXTRA_PREVIOUS_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                        + "a=ACTION_AUDIO_STATE_CHANGED, "
                        + "current s=" + stateToString(state) + ", "
                        + "pre s= " + stateToString(preState) + ", "
                        + "sb=" + isInitialStickyBroadcast());
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    // TODO: 2022/6/29
                    if (events != null) {
                        cancelTimer();
                        scoConnectAttempts = 0;
                        events.onBlueToothStarted();
                    }
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // TODO: 2022/6/29 by preState
                    if (events != null) {
                        events.onBlueToothStopped();
                    }
                }
            }
        }
    }

    public void setEvents(BluetoothEvents events) {
        this.events = events;
    }

    private void cancelTimer(){
        handler.removeCallbacks(timeRunnable);
    }

    private void startTimer(){
        handler.postDelayed(timeRunnable, BLUETOOTH_SCO_TIMEOUT_MS);
    }

    private void bluetoothTimeout(){
        ThreadUtils.checkIsOnMainThread();
        boolean scoConnected = false;
        List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
        if (devices.size() > 0) {
            bluetoothDevice = devices.get(0);
            if (bluetoothHeadset.isAudioConnected(bluetoothDevice)) {
                Log.d(TAG, "SCO connected with " + bluetoothDevice.getName());
                scoConnected = true;
            } else {
                Log.d(TAG, "SCO is not connected with " + bluetoothDevice.getName());
            }
        }

        if (scoConnected) {
            // We thought BT had timed out, but it's actually on; updating state.
            if (events != null){
                events.onBlueToothStarted();
            }
            scoConnectAttempts = 0;
        } else {
            // Give up and "cancel" our request by calling stopBluetoothSco().
            Log.w(TAG, "BT failed to connect after timeout");
            if (scoConnectAttempts < ATTEMPT_CONNECT_COUNT) {
                if (events != null) {
                    events.onStopBlueTooth();
                }
                scoConnectAttempts++;
                updateDevice();
            } else {
                if (events != null) {
                    events.onStopBlueTooth();
                    events.onBlueToothStartFailed(BluetoothError.START_TIMEOUT);
                }
            }
        }
    }

    public static RCBluetoothEventsManager create(Context context){
        return new RCBluetoothEventsManager(context);
    }

    public void close(){

    }

    private RCBluetoothEventsManager(Context context) {
        this.context = context;
        this.broadcastReceiver = new BluetoothHeadsetBroadcastReceiver();
        this.serviceListener = new BluetoothServiceListener();
        this.handler = new Handler(Looper.getMainLooper());
        this.timeRunnable = new Runnable() {
            @Override
            public void run() {
                bluetoothTimeout();
            }
        };
    }

    public void start(){
        if (!hasPermission(context, android.Manifest.permission.BLUETOOTH)){
            if (this.events != null){
                this.events.onBlueToothStartFailed(BluetoothError.NO_PERMISSION);
            }
            Log.e(TAG, "start failed for not get permission");
            return;
        }
        bluetoothHeadset = null;
        bluetoothDevice = null;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.bluetoothAdapter == null){
            Log.w(TAG, "device not support bluetooth");
            if (events != null){
                events.onBlueToothStartFailed(BluetoothError.NOT_SUPPORT);
            }
            return;
        }

        logBluetoothAdapterInfo(bluetoothAdapter);

        if (!getBluetoothProfileProxy(
                context, this.serviceListener, BluetoothProfile.HEADSET)) {
            Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed");
            if (events != null){
                events.onBlueToothStartFailed(BluetoothError.NOT_SUPPORT);
            }
            return;
        }

        IntentFilter bluetoothHeadsetFilter = new IntentFilter();
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        this.context.registerReceiver(broadcastReceiver,bluetoothHeadsetFilter);
    }

    private boolean getBluetoothProfileProxy(
            Context context, BluetoothProfile.ServiceListener listener, int profile) {
        return bluetoothAdapter.getProfileProxy(context, listener, profile);
    }

    public void stop(){
        if (this.bluetoothAdapter == null){
            return;
        }

        this.context.unregisterReceiver(broadcastReceiver);

        if (bluetoothHeadset != null){
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
            bluetoothHeadset = null;
        }

        bluetoothAdapter = null;
        bluetoothDevice = null;
    }

    @SuppressLint("HardwareIds")
    private void logBluetoothAdapterInfo(BluetoothAdapter localAdapter) {
        Log.d(TAG, "BluetoothAdapter: "
                + "enabled=" + localAdapter.isEnabled() + ", "
                + "state=" + stateToString(localAdapter.getState()) + ", "
                + "name=" + localAdapter.getName() + ", "
                + "address=" + localAdapter.getAddress());
        // Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
        Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            Log.d(TAG, "paired devices:");
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, " name=" + device.getName() + ", address=" + device.getAddress());
            }
        }
    }

    private String stateToString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothAdapter.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothAdapter.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothAdapter.STATE_DISCONNECTING:
                return "DISCONNECTING";
            case BluetoothAdapter.STATE_OFF:
                return "OFF";
            case BluetoothAdapter.STATE_ON:
                return "ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                // Indicates the local Bluetooth adapter is turning off. Local clients should immediately
                // attempt graceful disconnection of any remote links.
                return "TURNING_OFF";
            case BluetoothAdapter.STATE_TURNING_ON:
                // Indicates the local Bluetooth adapter is turning on. However local clients should wait
                // for STATE_ON before attempting to use the adapter.
                return  "TURNING_ON";
            default:
                return "INVALID";
        }
    }

    protected boolean hasPermission(Context context, String permission) {
        return context.checkPermission(permission, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检测是否已经有连接上的蓝牙设备可用，如果有，则通知开启蓝牙．做超时计时
     */
    private void updateDevice(){
        Log.d(TAG, "updateDevice: ");
        List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
        if (devices.isEmpty()){
            this.bluetoothDevice = null;
            Log.d(TAG, "No connected bluetooth headset");
            return;
        }else {
            this.bluetoothDevice = devices.get(0);
            if (events != null){
                events.onStartBlueTooth();
                startTimer();
            }
            Log.d(TAG, "Connected bluetooth headset: "
                    + "name=" + bluetoothDevice.getName() + ", "
                    + "state=" + stateToString(bluetoothHeadset.getConnectionState(bluetoothDevice))
                    + ", SCO audio=" + bluetoothHeadset.isAudioConnected(bluetoothDevice));

        }
        Log.d(TAG, "updateDevice: done");
    }

}

