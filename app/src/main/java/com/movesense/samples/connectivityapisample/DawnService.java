package com.movesense.samples.connectivityapisample;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class DawnService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Handler h = new Handler();
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 0, 22957, 9807), 0);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 50, 22957, 9807), 10*1000);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 100, 22957, 9807), 20*1000);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 150, 22957, 9807), 30*1000);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 200, 22957, 9807), 40*1000);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 250, 22957, 9807), 50*1000);
        h.postDelayed(() -> LedHelper.getInstance().setLed(0, 300, 22957, 9807), 60*1000);

        return START_NOT_STICKY;
    }
}
