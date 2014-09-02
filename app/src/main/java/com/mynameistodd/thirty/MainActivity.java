package com.mynameistodd.thirty;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.commands.engine.ThrottlePositionObdCommand;
import pt.lighthouselabs.obd.commands.protocol.EchoOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.LineFeedOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.ObdResetCommand;
import pt.lighthouselabs.obd.commands.protocol.SelectProtocolObdCommand;
import pt.lighthouselabs.obd.commands.protocol.TimeoutObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;


public class MainActivity extends Activity {

    private String TAG = "MYNAMEISTODD";
    private Context mContext = null;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket socket;

    private TextView deviceAddress;
    private TextView connectedStatus;
    private Button connect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        deviceAddress = (TextView) findViewById(R.id.device_address);
        connectedStatus = (TextView) findViewById(R.id.connected_status);
        connect = (Button) findViewById(R.id.connect);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBt, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        List<String> deviceStr = new ArrayList<String>();
        final List<String> devices = new ArrayList<String>();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceStr.add(device.getName() + "\n" + device.getAddress());
                devices.add(device.getAddress());
            }

            ArrayAdapter adapter = new ArrayAdapter(mContext, android.R.layout.select_dialog_singlechoice, deviceStr);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
            dialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    String address = devices.get(which);
                    Toast.makeText(mContext, address, Toast.LENGTH_SHORT).show();
                    deviceAddress.setText(address);

                    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("device", address);
                    editor.commit();
                }
            });
            dialog.setTitle("Choose a paired device.");
            dialog.show();

            connect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                    String deviceAddress = prefs.getString("device", "");

                    if (deviceAddress != "") {
                        if (socket != null && socket.isConnected()) {
                            try {
                                socket.close();
                                connectedStatus.setText("Not Connected");
                                connect.setText("Connect");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

                            try {
                                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                                socket.connect();

                                if (socket.isConnected()) {
                                    connectedStatus.setText("Connected!");
                                    connect.setText("Disconnect");

                                    try {
                                        new ObdResetCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        new EchoOffObdCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        new LineFeedOffObdCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        //new TimeoutObdCommand(60).run(socket.getInputStream(), socket.getOutputStream());
                                        new SelectProtocolObdCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());

                                        SpeedObdCommand speed = new SpeedObdCommand();
                                        ThrottlePositionObdCommand throttlePosition = new ThrottlePositionObdCommand();

                                        speed.run(socket.getInputStream(), socket.getOutputStream());
                                        throttlePosition.run(socket.getInputStream(), socket.getOutputStream());

                                        Log.d(TAG, "Speed: " + speed.getImperialSpeed());
                                        Log.d(TAG, "Throttle: " + throttlePosition.getPercentage());

                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                } else {
                    Toast.makeText(this, "Bluetooth is not enabled, exiting.", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
