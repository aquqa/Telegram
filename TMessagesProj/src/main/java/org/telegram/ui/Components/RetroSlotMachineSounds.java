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
        SPIN(R.raw.whisper_slots_spin, 1f),
        STOP(R.raw.whisper_slots_stop, 1f),
        WIN(R.raw.whisper_slots_win, 1f),
        MISS(R.raw.whisper_slots_miss, 1f),
        GESTURE(R.raw.whisper_slots_gesture, 1f),
        LEVER(R.raw.whisper_slots_lever, .8f),
        CLUNK(R.raw.whisper_slots_clunk, 1f),
        REEL(R.raw.whisper_slots_reel, .5f),
        COIN(R.raw.whisper_slots_coin, .7f),
        JACKPOT(R.raw.whisper_slots_jackpot, 1f);

        final int resource;
        final float volume;

        Cue(int resource, float volume) {
            this.resource = resource;
            this.volume = volume;
        }
    }

    private static final Cue[] CUES = Cue.values();
    private static final int CUE_PRIORITY = 1;
    // The reel loop outranks one-shot cues so a burst of coins never cuts it off.
    private static final int LOOP_PRIORITY = 2;
    private final int[] samples = new int[CUES.length];
    private final SparseBooleanArray ready = new SparseBooleanArray();
    private SoundPool pool;
    private int pendingSample;
    private float pendingRate;
    private float pendingVolume;
    private Cue loopCue;
    private int loopStream;

    void preload(Context context) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder()
                    .setMaxStreams(5)
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
                    if (status == 0) playSample(sample, pendingVolume, pendingRate);
                }
                if (status == 0 && loopCue != null && loopStream == 0 && sample == samples[loopCue.ordinal()]) {
                    startLoop(loopCue);
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
            pendingVolume = cue.volume;
        } else if (ready.valueAt(index)) {
            playSample(sample, cue.volume, rate);
        }
    }

    /** Loops {@code cue} until {@link #stopLoop}; starts once the sample has loaded. */
    void startLoop(Cue cue) {
        if (pool == null) return;
        if (loopStream != 0 && cue == loopCue) return;
        stopLoop();
        loopCue = cue;
        int sample = samples[cue.ordinal()];
        if (sample == 0 || !ready.get(sample)) return;
        try {
            loopStream = pool.play(sample, cue.volume, cue.volume, LOOP_PRIORITY, -1, 1f);
        } catch (RuntimeException e) {
            FileLog.e(e);
        }
    }

    void stopLoop() {
        loopCue = null;
        if (pool != null && loopStream != 0) {
            try {
                pool.stop(loopStream);
            } catch (RuntimeException e) {
                FileLog.e(e);
            }
        }
        loopStream = 0;
    }

    private void playSample(int sample, float volume, float rate) {
        try {
            pool.play(sample, volume, volume, CUE_PRIORITY, 0, rate);
        } catch (RuntimeException e) {
            FileLog.e(e);
        }
    }

    void release() {
        pendingSample = 0;
        loopCue = null;
        loopStream = 0;
        ready.clear();
        if (pool != null) {
            pool.setOnLoadCompleteListener(null);
            pool.release();
            pool = null;
        }
    }
}
