package io.tina.chris.meshaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener, View.OnClickListener {
    // Initializing variable(s)
    private static final String TAG = "MAIN";
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel mChannel;
    private WifiP2pManager mManager;
    private WifiScan mWifiScan;
    private Context mContext;
    private List<WifiP2pDevice> mPeers = new ArrayList();
    private ArrayList<String> data = new ArrayList<String>();
    private ListView lv;

    private Button startServiceButton;
    private Button stopServiceButton;
    private Button peerScanButton;
    private Button peerListButton;

    private Map record;
    private WifiP2pDnsSdServiceInfo serviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startServiceButton = (Button)findViewById(R.id.start_service);
        stopServiceButton = (Button)findViewById(R.id.stop_service);
        peerScanButton = (Button)findViewById(R.id.peer_scan);
        peerListButton = (Button)findViewById(R.id.peer_list);
        startServiceButton.setOnClickListener(this);
        stopServiceButton.setOnClickListener(this);
        peerScanButton.setOnClickListener(this);
        peerListButton.setOnClickListener(this);

        mContext = this;
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Gets an instance of the WifiP2pManager and calls its initialize() method.
        // The method returns a WifiP2pManager.Channel object, which is used later to
        // connect the app to thw Wi-Fi P2P framework.
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
    }

    private void startRegistration() {
        //  Create a string map containing information about your service.
        record = new HashMap();
        record.put("listenport", "10000");
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        // newInstance(String instanceName, String serviceType, Map<String, String> txtMap)
        serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        // public void addLocalService (WifiP2pManager.Channel c, WifiP2pServiceInfo servInfo,
        //                              WifiP2pManager.ActionListener listener)
        // Register a local service for service discovery. If a local service is registered,
        // the framework automatically responds to a service discovery request from a peer.
        mManager.addLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(mContext, "P2P service started", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Started P2P.");
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
            }
            @Override
            public void onFailure(int arg0) {
                Toast.makeText(mContext, "P2P service failed to start", Toast.LENGTH_LONG).show();
                Log.d(TAG, "P2P service start unsuccessful.");
            }
        });
    }

    private void stopBroadcastingP2P() {
        mManager.removeLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(mContext, "P2P service stopped", Toast.LENGTH_LONG).show();
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
            }
            @Override
            public void onFailure(int arg0) {
                Toast.makeText(mContext, "Failed to stop P2P service", Toast.LENGTH_LONG).show();
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            }
        });
    }

    private void scanPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Scan success.");
                Toast.makeText(mContext, "Wifi scan success", Toast.LENGTH_LONG).show();
                // Code for when the discovery initiation is successful goes here.
                // No services have actually been discovered yet, so this method
                // can often be left blank.  Code for peer discovery goes in the
                // onReceive method, detailed below.
            }
            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Scan unsuccessful code: " + reasonCode);
                // Code for when the discovery initiation fails goes here.
                // Alert the user that something went wrong.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWifiScan = new WifiScan();
        registerReceiver(mWifiScan, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mWifiScan);
    }

    // Invoked on requestPeers()
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mPeers.clear();
        mPeers.addAll(peers.getDeviceList());

        // If an AdapterView is backed by this data, notify it
        // of the change.  For instance, if you have a ListView of available
        // peers, trigger an update.
        if(mPeers.size() == 0) {
            Toast.makeText(mContext, "No devices found", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "No devices found");
            return;
        } else {
            data.clear();
            for(int i = 0; i < mPeers.size(); i++) {
                System.out.println("Peer: " + mPeers.get(i).deviceAddress + " " +
                                    mPeers.get(i).deviceName);
                data.add(i, mPeers.get(i).deviceAddress + " " + mPeers.get(i).deviceName);
            }
            lv = (ListView)findViewById(R.id.main_listview);
            lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, data));
        }
    }

    public void connect() {
//        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                WifiP2pDeviceList device = mPeers.get(position);
//                WifiP2pConfig config = new WifiP2pConfig();
//                config.deviceAddress = device.deviceAddress;
//                config.wps.setup = WpsInfo.PBC; // WpsInfo: A class representing Wi-Fi Protected Setup
//            }
//        });
        // Picking the first device found on the network.
        WifiP2pDevice device = mPeers.get(0);
        // WifiP2pConfig: a class representing a Wi-Fi P2p configuration for setting up a connection
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC; // WpsInfo: A class representing Wi-Fi Protected Setup

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(mContext, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        // InetAddress from WifiP2pInfo struct.
//        InetAddress groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
        String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();

        // After the group negotiation, we can determine the group owner.
        if(info.groupFormed && info.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a server thread and accepting
            // incoming connections.
        }else if (info.groupFormed) {
            // The other device acts as the client. In this case,
            // you'll want to create a client thread that connects to the group
            // owner.
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.start_service:
                startRegistration();
                break;
            case R.id.stop_service:
                stopBroadcastingP2P();
                break;
            case R.id.peer_scan:
                scanPeers();
                break;
            case R.id.peer_list:
                if (mManager != null) {
                    mManager.requestPeers(mChannel, (MainActivity)mContext);
                }
                break;
        }
    }

    class WifiScan extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "wifi is enabled");
                } else {
                    Log.d(TAG, "wifi is not enabled");
                }
            } else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on onPeersAvailable()
                if (mManager != null) {
                    mManager.requestPeers(mChannel, (MainActivity) mContext);
                }
                Log.d(TAG, "P2P peers changed");

            } else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if(mManager == null) {
                    return;
                }
                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if(networkInfo.isConnected()) {
                    // We are connected with the other device, request connection
                    // info to find group owner IP
                    mManager.requestConnectionInfo(mChannel, null);
                    Toast.makeText(mContext, "Connected!", Toast.LENGTH_SHORT).show();
                }
            } else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            }
        }
    }
}
