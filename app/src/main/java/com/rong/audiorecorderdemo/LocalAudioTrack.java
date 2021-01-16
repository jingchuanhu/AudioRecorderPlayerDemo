package com.rong.audiorecorderdemo;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.nio.ByteBuffer;

public class LocalAudioTrack {

  private static final String TAG = LocalAudioTrack.class.getSimpleName();
  private static AudioTrack audioTrack;
  private int sampleRate;
  private int bufferSize;
  // Requested size of each recorded buffer provided to the client.
  private static final int CALLBACK_BUFFER_SIZE_MS = 10;

  // Average number of callbacks per second.
  private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
  private static final int BITS_PER_SAMPLE = 16;
  private int channels;
  private boolean isAlive = false;

  public LocalAudioTrack(int sampleRate, int channels) {

    this.sampleRate = sampleRate;
    this.channels = channels;

    int minBufferSizeInBytes = AudioTrack
        .getMinBufferSize(sampleRate, channelCountToConfiguration(channels),
            AudioFormat.ENCODING_PCM_16BIT);
    audioTrack = createAudioTrack(sampleRate, channels);
  }

  private AudioTrack createAudioTrack(int sampleRate, int channels) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      int bufferSizeInBytes =
          AudioTrack.getMinBufferSize(
              sampleRate, channels, AudioFormat.ENCODING_PCM_16BIT);
      return new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
          new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate).setChannelMask(channels).build(), bufferSizeInBytes,
          AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
    } else {
      return new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
          channelCountToConfiguration(channels), AudioFormat.ENCODING_PCM_16BIT, bufferSize,
          AudioTrack.MODE_STREAM);
    }
  }

  public void start() {

    final int bytesPerFrame = channelCountToConfiguration(channels) * (BITS_PER_SAMPLE / 8);
    bufferSize = bytesPerFrame * (sampleRate / BUFFERS_PER_SECOND);
    audioTrack.play();
    isAlive = true;
//        trackThread = new TrackThread();
//        trackThread.isAlive = true;
//        trackThread.start();

  }

  public void playByte(ByteBuffer buffer) {

    if (!isAlive) {
      return;
    }
    buffer.rewind();

    int sizeInBytes = buffer.capacity();

    int bytesWritten = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      bytesWritten = writeOnLollipop(audioTrack, buffer, sizeInBytes);
    } else {
      bytesWritten = writePreLollipop(audioTrack, buffer, sizeInBytes);
    }

    if (bytesWritten != sizeInBytes) {
      Log.e(TAG, "AudioTrack.write failed: " + bytesWritten);
    }

  }

  public void stop() {

    isAlive = false;
    audioTrack.stop();
    audioTrack.flush();

  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
    return audioTrack.write(byteBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
  }

  private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
    return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
  }

  private int channelCountToConfiguration(int channels) {
    return (channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
  }


}
