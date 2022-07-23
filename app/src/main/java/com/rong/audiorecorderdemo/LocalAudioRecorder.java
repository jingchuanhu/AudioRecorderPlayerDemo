package com.rong.audiorecorderdemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class LocalAudioRecorder {
    private static final String TAG = "LocalAudioRecord";
//    public static final String TEMP_SD_CARD_PATH =
//            Environment.getExternalStorageDirectory() + File.separator + "TempRecord" + File.separator;
    private AudioRecord audioRecord;
    private int simpleRate;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    // Requested size of each recorded buffer provided to the client.
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;

    // Average number of callbacks per second.
    private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
    private int multiple = 1;
    private static int bufferSizeInBytes;
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private String filePath;
    private RecordThread recordThread;
//    private RecordCallBack recordCallBack;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int data;
    private short value;
    private ByteBuffer byteBuffer;
    private RecorderCallback callback;

    public interface RecorderCallback{

        void recorderBuffer(ByteBuffer buffer);
    }

    public LocalAudioRecorder(int simpleRate, int multiple) {
        this.simpleRate = simpleRate;
        this.multiple = multiple;

        initBuffer();
    }

    public void setRecordCallback(RecorderCallback callback){
        this.callback = callback;
    }

    public LocalAudioRecorder(int simpleRate) {
        this.simpleRate = simpleRate;
        initBuffer();
    }
//
//    public void setRecordCallBack(RecordCallBack recordCallBack) {
//        this.recordCallBack = recordCallBack;
//    }

//    static {
//        File file = new File(TEMP_SD_CARD_PATH + "index");
//        if (!file.exists()) {
//            boolean result = file.mkdirs();
//            if (!result) {
//                throw new RuntimeException("mkdir exception");
//            }
//        }
//    }

    public void stopRecord() {
        if (recordThread != null) {
            recordThread.cancel();
        }
        handler.removeCallbacksAndMessages(null);
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    public boolean isPause() {
        if (recordThread != null) {
            return recordThread.isPause();
        }
        return true;
    }

    public void pauseRecord() {
        if (recordThread != null) {
            recordThread.pauseRecord();
        }
    }

    public void resumeRecord() {
        if (recordThread != null) {
            recordThread.resumeRecord();
        }
    }

//    public void deleteAllFiles() {
//        File file = new File(TEMP_SD_CARD_PATH);
//        File[] files = file.listFiles();
//        if (files != null) {
//            for (File file1 : files) {
//                if (file1.isFile()) {
//                    file1.delete();
//                }
//            }
//        }
//    }

    public boolean startRecord(String fileName) {
        try {
            stopRecord();
            if (audioRecord == null) {
//                Math.max(BUFFER_SIZE_FACTOR * minBufferSize, byteBuffer.capacity());
                bufferSizeInBytes = AudioRecord.getMinBufferSize(this.simpleRate, CHANNEL, ENCODING);
//                LogMgr.d(TAG, "bufferSizeInBytes:" + bufferSizeInBytes);
                Log.d(TAG, "initRecordingInside audioRecorder params : rate " + simpleRate + " ,channel: " + CHANNEL
                        + " , audioformat: " + ENCODING + " preBuytes: " + bufferSizeInBytes);
                audioRecord =
                        new AudioRecord(MediaRecorder.AudioSource.MIC, simpleRate, CHANNEL, ENCODING,
                                bufferSizeInBytes);
            }

            filePath = fileName;
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                recordThread = new RecordThread(filePath);
                recordThread.start();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void initBuffer(){
        final int bytesPerFrame = CHANNEL * (BITS_PER_SAMPLE / 8);
        final int framesPerBuffer = simpleRate / BUFFERS_PER_SECOND;
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
    }

    public class RecordThread extends Thread {
        private boolean isRun = true;
        private boolean isPause = false;
        private OutputStream outputStream;
        private long size = 0;

        public RecordThread(String path) {
            try {
                outputStream = new FileOutputStream(path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public boolean isPause() {
            return isPause;
        }

        public void pauseRecord() {
            isPause = true;
        }

        public void resumeRecord() {
            isPause = false;
        }

        @Override public void run() {
            while (isRun) {

                if (isPause) {
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    byteBuffer.rewind();
                    byteBuffer.position(0);
                    int read = LocalAudioRecorder.this.audioRecord.read(byteBuffer, byteBuffer.capacity());
                    if (read > 0) {

                        if (callback != null){
                            callback.recorderBuffer(byteBuffer);
                        }
                    } else {
//                        LogMgr.e(TAG, "read error:" + read);
                        try {
                            Thread.sleep(20);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            LocalAudioRecorder.this.recordThread = null;
        }

        public void cancel() {
            try {
                isRun = false;
                join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private float calcVolume(byte[] buffer, int length) {
        if (length % 2 != 0) {
            return 0;
        }
        long v = 0;
        for (int i = 0; i < length; i += 2) {
            short data = (short) (((buffer[i + 1] & 0xff) << 8) | (buffer[i] & 0xff));
            v += data * data;
        }
        float mean = (1.0f * v) / (length / 2);
        float volume = (float) (10 * Math.log10(mean));
        return volume;
    }

    public int getSimpleRate() {
        return simpleRate;
    }

//    public void recordCallBack(final long size, final float volume) {
//        handler.post(new Runnable() {
//            @Override public void run() {
//                if (recordCallBack != null) {
//                    recordCallBack.recordCallBack(size, simpleRate, volume);
//                }
//            }
//        });
//    }
}

