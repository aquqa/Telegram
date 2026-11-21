package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.SparseBooleanArray;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/** Plays short arcade cues at the device's media volume. */
final class RetroSlotMachineSounds {
    enum Cue {
        SPIN(R.raw.whisper_slots_spin),
        STOP(R.raw.whisper_slots_stop),
        WIN(R.raw.whisper_slots_win),
        MISS(R.raw.whisper_slots_miss);

        final int resource;

        Cue(int resource) {
            this.resource = resource;
        }
    }

    private static final Cue[] CUES = Cue.values();
    private final int[] samples = new int[CUES.length];
    private final SparseBooleanArray ready = new SparseBooleanArray();
    private SoundPool pool;
    private int pendingSample;
    private float pendingRate;

    void preload(Context context) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            pool.setOnLoadCompleteListener((source, sample, status) -> {
                if (source != pool) return;
                ready.put(sample, status == 0);
                if (sample == pendingSample) {
                    pendingSample = 0;
                    if (status == 0) playSample(sample, pendingRate);
                }
            });
            for (Cue cue : CUES) {
                samples[cue.ordinal()] = pool.load(context, cue.resource, 1);
            }
        } catch (RuntimeException e) {
            FileLog.e(e);
            release();
        }
    }

    void play(Cue cue, float rate) {
        if (pool == null) return;
        int sample = samples[cue.ordinal()];
        if (sample == 0) return;
        pendingSample = 0;
        int index = ready.indexOfKey(sample);
        if (index < 0) {
            // Keep only the latest cue while the samples are still loading.
            pendingSample = sample;
            pendingRate = rate;
        } else if (ready.valueAt(index)) {
            playSample(sample, rate);
        }
    }

    private void playSample(int sample, float rate) {
        try {
            pool.play(sample, 1f, 1f, 1, 0, rate);
        } catch (RuntimeException e) {
            FileLog.e(e);
        }
    }

    void release() {
        pendingSample = 0;
        ready.clear();
        if (pool != null) {
            pool.setOnLoadCompleteListener(null);
            pool.release();
            pool = null;
        }
    }
}
