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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.SaveCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.commands.control.DtcNumberObdCommand;
import pt.lighthouselabs.obd.commands.engine.ThrottlePositionObdCommand;
import pt.lighthouselabs.obd.commands.protocol.EchoOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.LineFeedOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.ObdResetCommand;
import pt.lighthouselabs.obd.commands.protocol.OdbRawCommand;
import pt.lighthouselabs.obd.commands.protocol.SelectProtocolObdCommand;
import pt.lighthouselabs.obd.commands.protocol.TimeoutObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;
import pt.lighthouselabs.obd.exceptions.NoDataException;


public class MainActivity extends Activity {

    private String TAG = "MYNAMEISTODD";
    private Context mContext = null;
    private SharedPreferences prefs;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket socket;

    private TextView deviceAddress;
    private TextView connectedStatus;
    private TextView speed;
    private TextView throttlePosition;
    private TextView dtcs;
    private Button connect;
    private Button refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        setContentView(R.layout.activity_main);
        deviceAddress = (TextView) findViewById(R.id.device_address);
        connectedStatus = (TextView) findViewById(R.id.connected_status);
        speed = (TextView) findViewById(R.id.speed);
        throttlePosition = (TextView) findViewById(R.id.throttle_position);
        dtcs = (TextView) findViewById(R.id.dtc);
        connect = (Button) findViewById(R.id.connect);
        refresh = (Button) findViewById(R.id.refresh);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(mContext, "Bluetooth is not available", Toast.LENGTH_LONG).show();
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
        final List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceStr.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }

            ArrayAdapter adapter = new ArrayAdapter(mContext, android.R.layout.select_dialog_singlechoice, deviceStr);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
            dialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    final BluetoothDevice selectedDevice = devices.get(which);
                    String address = selectedDevice.getAddress();
                    Toast.makeText(mContext, address, Toast.LENGTH_SHORT).show();
                    deviceAddress.setText(address);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("device", address);
                    editor.apply();

                    //this probably needs to be somewhere else
                    ParseQuery<Device> query = ParseQuery.getQuery(Device.class);
                    query.whereEqualTo("address", address);
                    query.setCachePolicy(ParseQuery.CachePolicy.CACHE_ELSE_NETWORK);
                    query.findInBackground(new FindCallback<Device>() {
                        @Override
                        public void done(List<Device> devices, ParseException e) {
                            final Device myDevice; //needs to be somewhere accessible to everything.

                            if (devices.size() > 0) {
                                myDevice = devices.get(0);
                                saveDeviceObjectId(myDevice.getObjectId());
                            } else {
                                myDevice = new Device();
                                myDevice.setName(selectedDevice.getName());
                                myDevice.setAddress(selectedDevice.getAddress());
                                myDevice.setCapturedAt(new Date());
                                myDevice.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        saveDeviceObjectId(myDevice.getObjectId());
                                    }
                                });
                            }
                        }
                    });
                }
            });
            dialog.setTitle("Choose a paired device.");
            dialog.show();

            connect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                                mBluetoothAdapter.cancelDiscovery();
                                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                                socket.connect();

                                if (socket.isConnected()) {
                                    connectedStatus.setText("Connected!");
                                    connect.setText("Disconnect");

                                    try {
                                        new ObdResetCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        new EchoOffObdCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        new LineFeedOffObdCommand().run(socket.getInputStream(), socket.getOutputStream());
                                        new SelectProtocolObdCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());
                                        refresh();
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

            refresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });
        }
    }

    private void refresh() {
        if (socket != null && socket.isConnected()) {
            connectedStatus.setText("Connected!");
            connect.setText("Disconnect");

            try {
                SpeedObdCommand speedCmd = new SpeedObdCommand();
                ThrottlePositionObdCommand throttlePositionCmd = new ThrottlePositionObdCommand();
                DtcNumberObdCommand dtcCmd = new DtcNumberObdCommand();
                OdbRawCommand rawCmd = new OdbRawCommand("09 02");

                speedCmd.run(socket.getInputStream(), socket.getOutputStream());
                throttlePositionCmd.run(socket.getInputStream(), socket.getOutputStream());

                String dtcFormattedResult = "None";
                try {
                    dtcCmd.run(socket.getInputStream(), socket.getOutputStream());
                    dtcFormattedResult = dtcCmd.getFormattedResult();
                } catch (NoDataException e) {
                    e.printStackTrace();
                }

                String rawFormattedResult = "Unknown";
                try {
                    rawCmd.run(socket.getInputStream(), socket.getOutputStream());
                    rawFormattedResult = rawCmd.getFormattedResult();
                } catch (NoDataException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "Speed: " + speedCmd.getImperialSpeed());
                Log.d(TAG, "Throttle: " + throttlePositionCmd.getPercentage());
                Log.d(TAG, "DTCs: " + dtcFormattedResult);
                Log.d(TAG, "VIN: " + rawFormattedResult);

                speed.setText(String.valueOf(speedCmd.getImperialSpeed()));
                throttlePosition.setText(String.valueOf(throttlePositionCmd.getPercentage()));
                dtcs.setText(String.valueOf(dtcFormattedResult));

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(mContext, "Connect socket first!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            if (socket != null) {
                socket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                } else {
                    Toast.makeText(mContext, "Bluetooth is not enabled, exiting.", Toast.LENGTH_SHORT).show();
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

    private void saveDeviceObjectId(String deviceObjectId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("deviceObjectId", deviceObjectId);
        editor.apply();
    }
}
