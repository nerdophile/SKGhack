package com.example.android.skghack;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

//import com.example.skghack.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "skghack";
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static String address = "00:21:13:00:A4:F1";
    public boolean connected = false;

    private SensorManager sensorManager;
    private SensorManager getSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;
    TextView textlist;
    TextView textlist1;
    TextView textlist2;

    public boolean clicked = false;
    private float lastX, lastY, lastZ;

    int temp = 0;

    public float deltaX = 0;
    public float deltaY = 0;
    public float deltaZ = 0;
    public float startTime = System.currentTimeMillis();

    public float delGX = 0;
    public float delGY = 0;
    public float delGZ = 0;

    //public Button btn;

    private float vibrateThreshold = 0;

    private TextView currentX, currentY, currentZ, maxX, maxY, maxZ;

    private TextView gyroX, gyroY, gyroZ;

    public Vibrator v;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button1 = (Button) findViewById(R.id.button1);
        textlist = (TextView) findViewById(R.id.textlist);
        textlist1 = (TextView) findViewById(R.id.textlist1);
        textlist2 = (TextView) findViewById(R.id.textlist1);

        initialiseViews();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            // fail! we do not have a sensor
        }
        // initialise vibration
        getSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (getSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            gyroscope = getSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            getSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetoothTState();

        button1.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendData(curgx);
                sendData(curgy);
                sendData(curgz);
                sendData(curx);
                sendData(cury);
                sendData(curz);
                //    Toast.makeText(getBaseContext(), "Turn on LED", Toast.LENGTH_SHORT).show();

            }
        });
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if (Build.VERSION.SDK_INT >= 10) {
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[]{UUID.class});
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection", e);
            }
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    public void initialiseViews() {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);

        gyroX = (TextView) findViewById(R.id.GyroX);
        gyroY = (TextView) findViewById(R.id.GyroY);
        gyroZ = (TextView) findViewById(R.id.GyroZ);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        getSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);


        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e1) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e1.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "...Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        getSensorManager.unregisterListener(this);
        Log.d(TAG, "...In onPause()...");

        if (outStream != null) {
            try {
                outStream.flush();
            } catch (IOException e) {
                errorExit("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
            }
        }

        try {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBluetoothTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if (btAdapter == null) {
            errorExit("Fatal Error", "Bluetooth not support");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message) {
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private String sendData(String message) {
        byte[] msgBuffer = message.getBytes();

        Log.d(TAG, "...Send data: " + message + "...");

        try {
            outStream.write(msgBuffer);
        } catch (IOException e) {
            String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
            if (address.equals("00:00:00:00:00:00"))
                msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 35 in the java code";
            msg = msg + ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";

            errorExit("Fatal Error", msg);
        }
        return message;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //clean current values
        //displayCleanValues();
        // display the current values
//        displayCurrentValues();

        NewThread thread = new NewThread();
        thread.run();
        // display the max values
        //displayMaxValues();
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // get the change in x,y,z values of the accelerometer

            deltaX = sensorEvent.values[0];
            deltaY = sensorEvent.values[1];
            deltaZ = sensorEvent.values[2];
//            Thread newThread = new Thread(new ClientThread(deltaX,deltaY,deltaZ));
//            newThread.start();
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            delGX = sensorEvent.values[0];
            delGY = sensorEvent.values[1];
            delGZ = sensorEvent.values[2];
        }

    }

    public void displayMaxValues() {
        if (deltaX > deltaXMax) {
            deltaXMax = deltaX;
            maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax) {
            deltaYMax = deltaY;
            maxY.setText(Float.toString(deltaYMax));
        }
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }


    }

    String curgx;
    String curgy;
    String curgz;

    String curx;
    String cury;
    String curz;
    String a = "";
    String b = "";
    String c = "";

    public class NewThread implements Runnable {

        public void displayCurrentValues() {


            curx = Float.toString(deltaX);
            cury = Float.toString(deltaY);
            curz = Float.toString(deltaZ);


            curgx = "curgx" + Float.toString(delGX);
            curgy = "curgy" + Float.toString(delGY);
            curgz = "curgz" + Float.toString(delGZ);

            startTime = System.currentTimeMillis();

            a = a + " " + curx;
            b = b + " " + cury;
            c = c + " " + curz;

            try {
                a = a + " " + curx;
                b = b + " " + cury;
                c = c + " " + curz;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textlist.setText(a);
                        textlist1.setText(b);
                        textlist2.setText(c);

                        currentX.setText(curx);
                        currentY.setText(cury);
                        currentZ.setText(curz);

                        gyroX.setText(curgx);
                        gyroY.setText(curgy);
                        gyroZ.setText(curgz);

                        textlist.setText(a);
                        textlist1.setText(b);
                        textlist2.setText(c);
                        Log.d("SKG", "a=" + a + " b=" + b + " c=" + c);


                        gyroX.setText(curgx);
                        gyroY.setText(curgy);
                        gyroZ.setText(curgz);
                    }
                });


                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Log.d("SKG", "run");

            displayCurrentValues();

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}


