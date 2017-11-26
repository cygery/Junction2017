package com.movesense.samples.connectivityapisample;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.mds.internal.connectivity.MovesenseConnectedDevices;
import com.movesense.samples.connectivityapisample.model.InfoResponse;
import com.movesense.samples.connectivityapisample.model.LinearAcceleration;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscription;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener, TimePickerFragment.TimeSetListener {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;

    // MDS
    private Mds mMds;
    public static final String URI_CONNECTEDDEVICES = "suunto://MDS/ConnectedDevices";
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";
    private final String LINEAR_ACCELERATION_PATH = "Meas/Acc/";
    private final String LINEAR_INFO_PATH = "/Meas/Acc/Info";

    public static final double G = 9.80665;

    public final double THRESHOLD_MOVE = 1.5;
    private double filterWeight = 1.;
    private static final int GUARD_TIME_MS = 2*1000;

    private enum STATUS {IDLE, STANDING_UP, SITTING_DOWN};
    private STATUS status;

    private static final int RING_COUNTER_SIZE = 8;
    private RingBuffer ringBuffer;
    private long timeOfLastStatusChange = -1;

    SharedPreferences settings;

    @BindView(R.id.x_axis_textView) TextView xAxisTextView;
    @BindView(R.id.y_axis_textView) TextView yAxisTextView;
    @BindView(R.id.z_axis_textView) TextView zAxisTextView;
    @BindView(R.id.int1_textView) TextView int1TextView;
    @BindView(R.id.int2_textView) TextView int2TextView;
    @BindView(R.id.liveSwitch) Switch liveSwitch;
    @BindView(R.id.ignoreTimeSwitch) Switch ignoreTimeSwitch;
    @BindView(R.id.dawnSimulatorSwitch) Switch dawnSimulatorSwitch;
    @BindView(R.id.timepickerDawnSimulatorButton) Button timepickerDawnSimulatorButton;
    @BindView(R.id.simulateDawnButton) Button simulateDawnButton;

    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean firstReading = true;

    // BleClient singleton
    static private RxBleClient mBleClient;
    private MdsSubscription mdsSubscription;

    // UI
    private ListView mScanResultListView;
    private ArrayList<MyScanResult> mScanResArrayList = new ArrayList<>();
    ArrayAdapter<MyScanResult> mScanResArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        ringBuffer = new RingBuffer(RING_COUNTER_SIZE);

        status = STATUS.IDLE;

        // Init Scan UI
        mScanResultListView = (ListView)findViewById(R.id.listScanResult);
        mScanResArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mScanResArrayList);
        mScanResultListView.setAdapter(mScanResArrayAdapter);
        mScanResultListView.setOnItemLongClickListener(this);
        mScanResultListView.setOnItemClickListener(this);

        // Make sure we have all the permissions this app needs
        requestNeededPermissions();

        // Initialize Movesense MDS library
        initMds();

        timepickerDawnSimulatorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment f = new TimePickerFragment();
                f.show(getFragmentManager(), "timePicker");
            }
        });

        dawnSimulatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            timepickerDawnSimulatorButton.setEnabled(isChecked);
            if (isChecked) {
                scheduleAlarm();
            }
        });

        simulateDawnButton.setOnClickListener(v -> startService(new Intent(MainActivity.this, DawnService.class)));
    }

    private void scheduleAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        int h = settings.getInt("alarm_hour", 13);
        int m = settings.getInt("alarm_minute", 37);


        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, h);
        calendar.set(Calendar.MINUTE, m);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DATE,1);
        }

        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), PendingIntent.getBroadcast(this, 1, new Intent("com.movesense.samples.connectivityapisample.alarm"), PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private RxBleClient getBleClient() {
        // Init RxAndroidBle (Ble helper library) if not yet initialized
        if (mBleClient == null)
        {
            mBleClient = RxBleClient.create(this);
        }

        return mBleClient;
    }

    private void initMds() {
        mMds = Mds.builder().build(this);
    }

    void requestNeededPermissions()
    {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

    Subscription mScanSubscription;
    public void onScanClicked(View view) {
        findViewById(R.id.buttonScan).setVisibility(View.GONE);
        findViewById(R.id.buttonScanStop).setVisibility(View.VISIBLE);

        // Start with empty list
        mScanResArrayList.clear();
        mScanResArrayAdapter.notifyDataSetChanged();

        mScanSubscription = getBleClient().scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.d(LOG_TAG,"scanResult: " + scanResult);

                            // Process scan result here. filter movesense devices.
                            if (scanResult.getBleDevice()!=null &&
                                    scanResult.getBleDevice().getName() != null &&
                                    scanResult.getBleDevice().getName().startsWith("Movesense")) {

                                // replace if exists already, add otherwise
                                MyScanResult msr = new MyScanResult(scanResult);
                                if (mScanResArrayList.contains(msr))
                                    mScanResArrayList.set(mScanResArrayList.indexOf(msr), msr);
                                else
                                    mScanResArrayList.add(0, msr);

                                mScanResArrayAdapter.notifyDataSetChanged();
                            }
                        },
                        throwable -> {
                            Log.e(LOG_TAG,"scan error: " + throwable);
                            // Handle an error here.

                            // Re-enable scan buttons, just like with ScanStop
                            onScanStopClicked(null);
                        }
                );
    }

    public void onScanStopClicked(View view) {
        if (mScanSubscription != null)
        {
            mScanSubscription.unsubscribe();
            mScanSubscription = null;
        }

        findViewById(R.id.buttonScan).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonScanStop).setVisibility(View.GONE);
    }

    void showDeviceInfo(final String serial) {
        String uri = SCHEME_PREFIX + serial + "/Info";
        final Context ctx = this;
        mMds.get(SCHEME_PREFIX
                 + MovesenseConnectedDevices.getConnectedDevice(0).getSerial() + LINEAR_INFO_PATH,
                  null, new MdsResponseListener() {
                    @Override
                    public void onSuccess(String data) {
                        Log.d(LOG_TAG, "onSuccess(): " + data);

                        InfoResponse infoResponse = new Gson().fromJson(data, InfoResponse.class);
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "onError(): ", error);
                    }
                });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return;

        MyScanResult device = mScanResArrayList.get(position);
        if (!device.isConnected()) {
            // Stop scanning
            onScanStopClicked(null);

            // And connect to the device
            connectBLEDevice(device);
        }
        else {
            // Device is connected, trigger showing /Info
            showDeviceInfo(device.connectedSerial);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return false;

        MyScanResult device = mScanResArrayList.get(position);

        unSubscribe();

        Log.i(LOG_TAG, "Disconnecting from BLE device: " + device.macAddress);
        mMds.disconnect(device.macAddress);

        return true;
    }

    private void startListening(String serial) {
        String rate = "13";
        mdsSubscription = Mds.builder()
                             .build(this)
                             .subscribe(URI_EVENTLISTENER, FormatHelper.formatContractToJson(
                                     serial,
                                     LINEAR_ACCELERATION_PATH + rate), new MdsNotificationListener() {
                                 @Override
                                 public void onNotification(String data) {
                                     Log.d(LOG_TAG, "onSuccess(): " + data);

                                     LinearAcceleration linearAccelerationData = new Gson().fromJson(data,
                                                                                                     LinearAcceleration.class);

                                     if (linearAccelerationData != null) {

                                         LinearAcceleration.Array arrayData = linearAccelerationData.body.array[0];

                                         xAxisTextView.setText(
                                                 String.format(Locale.getDefault(), "x: %.6f", arrayData.x));
                                         yAxisTextView.setText(
                                                 String.format(Locale.getDefault(), "y: %.6f", arrayData.y));
                                         zAxisTextView.setText(
                                                 String.format(Locale.getDefault(), "z: %.6f", arrayData.z));

                                         if (firstReading) {
                                             lastX = arrayData.x;
                                             lastY = arrayData.y;
                                             lastZ = arrayData.z;
                                             firstReading = false;
                                         }

                                         lastX = filterWeight*arrayData.x + (1-filterWeight) * lastX;
                                         lastY = filterWeight*arrayData.y + (1-filterWeight) * lastY;
                                         lastZ = filterWeight*arrayData.z + (1-filterWeight) * lastZ;

                                         ringBuffer.append(Math.sqrt(lastY*lastY+lastZ*lastZ)-G);

                                         updateIntegrals();
                                     }
                                 }

                                 @Override
                                 public void onError(MdsException error) {
                                     Log.e(LOG_TAG, "onError(): ", error);

                                     Toast.makeText(MainActivity.this, error.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                 }
                             });
    }

    private void updateIntegrals() {
        double int1 = 0;
        double int2 = 0;

        // alternative: find min/max in range, require thresholds to be met, compare index of min/max
        for (int i = 0; i < RING_COUNTER_SIZE/2; i++) {
            double d = ringBuffer.get(i);
            int1 += d;
        }
        int1 /= (RING_COUNTER_SIZE/2);
        for (int i = RING_COUNTER_SIZE/2; i < RING_COUNTER_SIZE; i++) {
            double d = ringBuffer.get(i);
            int2 += d;
        }
        int2 /= (RING_COUNTER_SIZE/2);

        if (timeOfLastStatusChange + GUARD_TIME_MS <= System.currentTimeMillis()) {
            if (int1 > THRESHOLD_MOVE && int2 < -THRESHOLD_MOVE) {
                if (status == STATUS.IDLE) {
                    status = STATUS.STANDING_UP;
                    timeOfLastStatusChange = System.currentTimeMillis();
                    handleStandUp();
                }
            } else if (int1 < -THRESHOLD_MOVE && int2 > THRESHOLD_MOVE) {
                if (status == STATUS.IDLE) {
                    status = STATUS.SITTING_DOWN;
                    timeOfLastStatusChange = System.currentTimeMillis();
                    handleSitDown();
                }
            } else {
                if (status == STATUS.STANDING_UP || status == STATUS.SITTING_DOWN) {
                    status = STATUS.IDLE;
                    timeOfLastStatusChange = System.currentTimeMillis();
                    handleIdle();
                }
            }
        }

        int1TextView.setText(String.format(Locale.getDefault(), "int1: %.6f", int1));
        int2TextView.setText(String.format(Locale.getDefault(), "int2: %.6f", int2));

        Log.d(LOG_TAG, "int1: " + int1 + " int2: " + int2 + " status: " + status.name());
    }

    private void unSubscribe() {
        if (mdsSubscription != null) {
            mdsSubscription.unsubscribe();
            mdsSubscription = null;
        }
    }

    private void handleStandUp() {
        Toast.makeText(this, "STANDING_UP", Toast.LENGTH_SHORT).show();
        if (liveSwitch.isChecked() && (isEvening() || ignoreTimeSwitch.isChecked())) {
            LedHelper.getInstance().setLed(0, 500, 10574, 10781);
        }
    }

    private void handleSitDown() {
        Toast.makeText(this, "SITTING_DOWN", Toast.LENGTH_SHORT).show();
        if (liveSwitch.isChecked() && (isEvening() || ignoreTimeSwitch.isChecked())) {
            LedHelper.getInstance().setLed(0, 200, 22957, 9807);
        }
    }

    private boolean isEvening() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour >= 16 && hour <= 3) {
            return true;
        }

        return false;
    }

    private void handleIdle() {
        Toast.makeText(this, "IDLE", Toast.LENGTH_SHORT).show();
    }

    private void connectBLEDevice(MyScanResult device) {
        RxBleDevice bleDevice = getBleClient().getBleDevice(device.macAddress);

        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        Log.i(LOG_TAG, "Connecting to BLE device: " + bleDevice.getMacAddress());
        mMds.connect(bleDevice.getMacAddress(), new MdsConnectionListener() {

            @Override
            public void onConnect(String s) {
                Log.d(LOG_TAG, "onConnect:" + s);
            }

            @Override
            public void onConnectionComplete(String macAddress, String serial) {
                for (MyScanResult sr : mScanResArrayList) {
                    if (sr.macAddress.equalsIgnoreCase(macAddress)) {
                        sr.markConnected(serial);
                        break;
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();

                startListening(device.connectedSerial);

                Toast.makeText(MainActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "onError:" + e);

                showConnectionError(e);
            }

            @Override
            public void onDisconnect(String bleAddress) {
                Log.d(LOG_TAG, "onDisconnect: " + bleAddress);
                for (MyScanResult sr : mScanResArrayList) {
                    if (bleAddress.equals(sr.macAddress))
                        sr.markDisconnected();
                }
                mScanResArrayAdapter.notifyDataSetChanged();

                unSubscribe();

                Toast.makeText(MainActivity.this, "Disconnected!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showConnectionError(MdsException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connection Error:")
                .setMessage(e.getMessage());

        builder.create().show();
    }

    @Override
    public void onTimeSet(int hourOfDay, int minute) {
        timepickerDawnSimulatorButton.setText(String.format("%02d:%02d", hourOfDay, minute));
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("alarm_hour", hourOfDay);
        editor.putInt("alarm_minute", minute);
        editor.apply();
        if (dawnSimulatorSwitch.isChecked()) {
            scheduleAlarm();
        }
    }
}
