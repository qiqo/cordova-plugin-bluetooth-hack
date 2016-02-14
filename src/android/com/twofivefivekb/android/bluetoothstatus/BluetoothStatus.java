package com.twofivefivekb.android.bluetoothstatus;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;
import android.os.ParcelUuid;

import java.lang.Exception;
import java.util.UUID;
import java.io.OutputStream;
import java.util.Arrays;

public class BluetoothStatus extends CordovaPlugin {
    private static CordovaWebView mwebView;
    private static CordovaInterface mcordova;

    private static final String LOG_TAG = "BluetoothStatus";
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("enableBT")) {
            enableBT();
            return true;
        } else if (action.equals("promptForBT")) {
            promptForBT();
            return true;
        } else if(action.equals("initPlugin")) {
            initPlugin();
            return true;
        }
        else if(action.equals("makeVisible")) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            mcordova.getActivity().startActivity(intent);
            return true;
        }
        else if(action.equals("setName")) {
            bluetoothAdapter.setName("ICH");
            return true;
        }
        else if(action.equals("test")) {
            try
            {
              if(args.getString(0).equals("discovery"))
              {
                log(args.getString(0));
                bluetoothAdapter.startDiscovery();                
              }
              if(args.getString(0).equals("uuids"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                device.fetchUuidsWithSdp();
              }
              if(args.getString(0).equals("pair"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                boolean b= device.setPin(new byte[]{0,0,0,0});
                log("pin "+b);
                device.createBond();
              }
              if(args.getString(0).equals("connect"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                UUID u = UUID.fromString(args.getString(2));
                BluetoothSocket s= device.createInsecureRfcommSocketToServiceRecord(u);
                s.connect();
                OutputStream o =s.getOutputStream();
                o.write("Sesam öffne dich!\n\r".getBytes());
                o.flush();
              }
              if(args.getString(0).equals("state"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                log("getAddress() "+device.getAddress());
                log("getBondState() "+device.getBondState());
                log("getName() "+device.getName());
                log("getType() "+device.getType());
                log("toString() "+device.toString());
              }
            }
            catch(Exception e)
            {
              log(e.toString());
            }
            
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mcordova.getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mwebView = super.webView;
        mcordova = cordova;

        bluetoothManager = (BluetoothManager) webView.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        filter.addAction(BluetoothDevice.ACTION_UUID);
        
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        mcordova.getActivity().registerReceiver(mReceiver, filter);
    }

    private void enableBT() {
        //enable bluetooth without prompting
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "Bluetooth is not supported");
        } else if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
    }

    private void promptForBT() {
        //prompt user for enabling bluetooth
        Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mcordova.getActivity().startActivity(enableBTIntent);
    }

    private void initPlugin() {
        //test if B supported
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "Bluetooth is not supported");
        } else {
            Log.e(LOG_TAG, "Bluetooth is supported");

            sendJS("javascript:cordova.plugins.BluetoothStatus.hasBT = true;");

            //test if BLE supported
            if (!mcordova.getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Log.e(LOG_TAG, "BluetoothLE is not supported");
            } else {
                Log.e(LOG_TAG, "BluetoothLE is supported");
                sendJS("javascript:cordova.plugins.BluetoothStatus.hasBTLE = true;");
            }

            //test if BT enabled
            if (bluetoothAdapter.isEnabled()) {
                Log.e(LOG_TAG, "Bluetooth is enabled");

                sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = true;");
                sendJS("javascript:cordova.fireWindowEvent('BluetoothStatus.enabled');");
            } else {
                Log.e(LOG_TAG, "Bluetooth is not enabled");
            }
        }
    }

    private void sendJS(final String js) {
        mcordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mwebView.loadUrl(js);
            }
        });
    }

    private void log(final String log) {
        sendJS("javascript:console.log('"+log+"');");
    }

    //broadcast receiver for BT intent changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.e(LOG_TAG, "Bluetooth was disabled");

                        sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = false;");
                        sendJS("javascript:cordova.fireWindowEvent('BluetoothStatus.disabled');");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.e(LOG_TAG, "Bluetooth was enabled");

                        sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = true;");
                        sendJS("javascript:cordova.fireWindowEvent('BluetoothStatus.enabled');");

                        break;
                }
            }
            
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                // Get the BluetoothDevice object from the Intent
                log("!!!!!!!!!!!!! ");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log("DeviceList " + device.getName() + "\n" + device.getAddress());
            }
            
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                log("Discovery Finished");
            }
            
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                log("Discovery Started");
            }

            if (action.equals(BluetoothDevice.ACTION_UUID)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log("BluetoothDevice.ACTION_UUID "+device.getName());
                for(ParcelUuid u: device.getUuids())
                {
                  log(u.toString());                  
                }
            }

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log("BluetoothDevice.ACTION_BOND_STATE_CHANGED "+device.getName()+" "+device.getBondState());
            }
            
        }
    };
        
}
