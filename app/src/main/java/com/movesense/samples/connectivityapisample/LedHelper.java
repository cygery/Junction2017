package com.movesense.samples.connectivityapisample;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LedHelper {
    private static LedHelper instance;
    private static OkHttpClient client;

    private LedHelper() {
        client = new OkHttpClient();
    }

    public static LedHelper getInstance() {
        if (instance == null) {
            instance = new LedHelper();
        }
        return instance;
    }


    public void setLed(int device, int level, int colorX, int colorY)  {
        String url = ApiKeys.HELVAR_URL + String.format("/v1/command?device=%d&level=%d&colour_x=%d&colour_y=%d", device, level, colorX, colorY);

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("LedHelper", "HTTP Failure: " + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("LedHelper", "HTTP response: " + response.body().string());
            }
        });
    }
}
