package com.dsh.tp;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * 用麦克风音量判断「现在有没有在说话」。
 * 自适应噪声底 + 260ms 拖尾，避免环境声和换气把滚动打断得太碎。
 */
final class VoiceFollower {

    interface Listener {
        void onSpeaking(boolean speaking);
    }

    private static final int RATE = 16000;
    private static final long HOLD_MS = 260L;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private AudioRecord record;
    private Thread thread;
    private volatile boolean running;

    VoiceFollower(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    boolean start() {
        if (running) {
            return true;
        }
        int min = AudioRecord.getMinBufferSize(RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            min = RATE / 2;
        }
        int bufferBytes = Math.max(min, RATE); // 约 0.5 秒
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
        } catch (Exception e) {
            record = null;
            return false;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            record = null;
            return false;
        }
        try {
            record.startRecording();
        } catch (Exception e) {
            record.release();
            record = null;
            return false;
        }
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "voice-follow");
        thread.start();
        return true;
    }

    void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(900);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (record != null) {
            try {
                record.stop();
            } catch (Exception ignored) {
            }
            record.release();
            record = null;
        }
    }

    private void loop() {
        final short[] buffer = new short[1024];
        double floor = 0.006;
        long lastVoice = 0L;
        boolean speaking = false;
        long lastEmit = 0L;

        while (running) {
            AudioRecord rec = record;
            if (rec == null) {
                break;
            }
            int read;
            try {
                read = rec.read(buffer, 0, buffer.length);
            } catch (Exception e) {
                break;
            }
            if (read <= 0) {
                continue;
            }
            double sum = 0;
            for (int i = 0; i < read; i++) {
                double v = buffer[i] / 32768.0;
                sum += v * v;
            }
            double rms = Math.sqrt(sum / read);
            long now = SystemClock.uptimeMillis();

            // 噪声底：安静时慢慢跟，变响时不动
            if (rms < floor * 2.0) {
                floor = floor * 0.99 + rms * 0.01;
            }
            double threshold = Math.max(0.010, floor * 2.6);
            if (rms > threshold) {
                lastVoice = now;
            }
            boolean next = (now - lastVoice) < HOLD_MS;
            if (next != speaking) {
                speaking = next;
                emit(speaking);
                lastEmit = now;
            } else if (now - lastEmit > 1500L) {
                // 长时间静音时重发一次，防止界面状态丢失
                emit(speaking);
                lastEmit = now;
            }
        }
        if (speaking) {
            emit(false);
        }
    }

    private void emit(final boolean speaking) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSpeaking(speaking);
                }
            }
        });
    }
}
