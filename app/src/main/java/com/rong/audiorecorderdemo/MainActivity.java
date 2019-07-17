package com.rong.audiorecorderdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    List<String> unGrantedPermissions;
    LocalAudioRecorder recorder;
    AppRTCAudioManager audioManager;
    LocalAudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recorder = new LocalAudioRecorder(44100);
        recorder.setRecordCallback(new RecordCallback());
        audioTrack = new LocalAudioTrack(44100, 1);
        audioManager = AppRTCAudioManager.create(getApplicationContext(), new Runnable() {
            @Override
            public void run() {

            }
        });

        audioManager.init();
    }

    private void startRecorder(){

        String dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)+"/";
        File dir = new File(dirPath);
        if (!dir.exists()){
            dir.mkdirs();
        }
        DateFormat df = new SimpleDateFormat("ddHHm-m-ss");
        String filePath = dirPath + df.format(new Date()) + ".pcm";

        recorder.startRecord(filePath);
        audioTrack.start();

    }

    public void checkPermissions() {
        unGrantedPermissions = new ArrayList();
        for (String permission : MANDATORY_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                unGrantedPermissions.add(permission);
            }
        }
        if (unGrantedPermissions.size() == 0) {//已经获得了所有权限，开始加入聊天室


        } else {//部分权限未获得，重新请求获取权限
            String[] array = new String[unGrantedPermissions.size()];
            ActivityCompat.requestPermissions(this, unGrantedPermissions.toArray(array), 0);
        }
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        unGrantedPermissions.clear();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                unGrantedPermissions.add(permissions[i]);
        }
        for (String permission : unGrantedPermissions) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
//                showToastLengthLong(getString(R.string.PermissionStr) + permission + getString(R.string.plsopenit));
                finish();
            } else ActivityCompat.requestPermissions(this, new String[]{permission}, 0);
        }
        if (unGrantedPermissions.size() == 0) {

        }
    }


    public class RecordCallback implements LocalAudioRecorder.RecorderCallback{


        @Override
        public void recorderBuffer(ByteBuffer buffer) {

            audioTrack.playByte(buffer);
        }
    }

    public void onClick(View view) {

        switch (view.getId()){
            case R.id.start_recorder:{

                startRecorder();
                break;
            }

            case R.id.stop_recorder:{

                recorder.stopRecord();
                audioTrack.stop();
                break;
            }
            default:{


            }
        }

        checkPermissions();
    }
}
