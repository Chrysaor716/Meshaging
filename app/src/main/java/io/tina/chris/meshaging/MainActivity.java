package io.tina.chris.meshaging;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener,
        View.OnClickListener, AdapterView.OnItemClickListener, WifiP2pManager.ConnectionInfoListener,
        WifiP2pManager.GroupInfoListener {
    // Initializing variable(s)
    private static final String TAG = "MAIN";
    private static final int SERVER_PORT = 1030;
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel mChannel;
    private WifiP2pManager mManager;
    private WifiScan mWifiScan;
    private Context mContext;
    private List<WifiP2pDevice> mPeers = new ArrayList();
    private ArrayList<String> data = new ArrayList<String>();
    private ListView lv;
    private WifiP2pInfo p2pInfo;

    private Button startServiceButton;
    private Button stopServiceButton;
    private Button peerScanButton;
    private Button peerListButton;
    private TextView groupInfoText;

    private Map record;
    private WifiP2pDnsSdServiceInfo serviceInfo;
    ArrayAdapter arrayAdapter;
    private ArrayList<InetAddress> clients = new ArrayList<InetAddress>();

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

        groupInfoText = (TextView)findViewById(R.id.group_info);

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

        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, data);
        // Click listener for listview in UI that lists available peers
        lv = (ListView) findViewById(R.id.main_listview);
        lv.setAdapter(arrayAdapter);
        lv.setOnItemClickListener(this);
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
        // If discoverPeers() does find peers, acton WIFI_P2P_PEERS_CHANGED_ACTION will be triggered
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
                Toast.makeText(mContext, "Wifi scan unsuccessful. Retry.", Toast.LENGTH_LONG).show();
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

    @Override
    protected void onStop() {
        super.onStop();
        //
    }

    // Invoked on requestPeers()
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mPeers.clear();
        mPeers.addAll(peers.getDeviceList()); // "peers" parameter = discovered peers

        // If an AdapterView is backed by this data, notify it
        // of the change.  For instance, if you have a ListView of available
        // peers, trigger an update.
        if (mPeers.size() == 0) {
            Toast.makeText(mContext, "No devices found", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "No devices found");
            data.clear();
            return;
        } else {
            data.clear();
            for (int i = 0; i < mPeers.size(); i++) {
                System.out.println("Peer: " + mPeers.get(i).deviceAddress + " " + mPeers.get(i).deviceName);
                data.add(i, mPeers.get(i).deviceAddress + " " + mPeers.get(i).deviceName);
            }
            // Update the list view in the UI
            arrayAdapter.notifyDataSetChanged();
        }
    }

    private void connect(int index) {
        // Picking the device based on the position on the list that the user clicked on in List View
        WifiP2pDevice device = mPeers.get(index);
        // WifiP2pConfig: a class representing a Wi-Fi P2p configuration for setting up a connection
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC; // WpsInfo: A class representing Wi-Fi Protected Setup
        // Notifies you when the initiation succeeds or fails
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connect failed. Retry. Reason: " + reason);
                Toast.makeText(mContext, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    // Listens for changes in connection state
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        p2pInfo = info;                     // Interface for callback when group info is available
//        mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
//            @Override   // Invoked when the requested P2P group info is available
//            public void onGroupInfoAvailable(WifiP2pGroup group) {
//                CharSequence chseq = "Group Owner: ";
//                groupInfoText.setText(chseq);
//                groupInfoText.append(group.getOwner().deviceName);
//            }
//        });
        if(info.isGroupOwner) { // Notify group owner
            Toast.makeText(mContext, "You are the group owner", Toast.LENGTH_SHORT).show();
        } else if(info.groupFormed) { // Notify non-group owner
            Toast.makeText(mContext, "You are not the group owner", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        groupInfoText.append(group.getOwner().deviceName);
        if(group.isGroupOwner()) {
            Log.d(TAG, "This device is the group owner.");
        } else {
            Log.d(TAG, "This device is NOT the group owner.");
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
            // WIFI_P2P_PEERS_CHANGED_ACTION automatically invokes requestPeers(), but this button
            // can be clicked to update peer list.
            case R.id.peer_list:
                if (mManager != null) {
                    mManager.requestPeers(mChannel, (MainActivity) mContext);
                }
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        System.out.println(mPeers.get(0));
        connect(position);
    }

    class WifiScan extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Determine if Wifi P2P mode is enabled or not, alert the Activity.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "Wifi is enabled");
                } else {
                    Log.d(TAG, "Wifi is not enabled");
                    Toast.makeText(mContext, "Wifi is not enabled", Toast.LENGTH_SHORT).show();
                }
            } else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on onPeersAvailable()
                if(mManager != null) {
                    mManager.requestPeers(mChannel, (MainActivity) mContext);
                }
                Log.d(TAG, "P2P peers changed");
                // This is also triggered when the devices connect
            } else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if(mManager == null) {
                    return;
                }
                NetworkInfo networkInfo = (NetworkInfo)intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if(networkInfo != null) {
                    Log.d(TAG, networkInfo.toString());
                    if(networkInfo.isConnected()) {
                        // We are connected with the other device, request connection
                        // info to find group owner IP
                        mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                if(info.groupFormed && info.isGroupOwner) { // Notify group owner; server
                                    Toast.makeText(mContext, "You are the group owner", Toast.LENGTH_SHORT).show();
                                    CharSequence chseq = "Group Owner: ";
                                    groupInfoText.setText(chseq);
                                    groupInfoText.append(info.groupOwnerAddress.toString());
//                                    // Do in background task
//                                    clients.clear();
//                                    ServerSocket serverSocket = null;
//                                    try {
//                                        serverSocket = new ServerSocket(SERVER_PORT);
//                                         //There could be multiple clients; grab their IP addresses
//                                        while(true) {
//                                            Socket clientSocket = serverSocket.accept();
//                                            clients.add(clientSocket.getInetAddress());
//                                            clientSocket.close();
//                                        }
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
                                    new ServerAsyncTask(mContext).execute();
                                } else if(info.groupFormed) { // Notify non-group owner; client
                                    Toast.makeText(mContext, "You are not the group owner", Toast.LENGTH_SHORT).show();
                                    CharSequence chseq = "Group Owner: ";
                                    groupInfoText.setText(chseq);
                                    groupInfoText.append(info.groupOwnerAddress.toString());

                                    String ownerAddr = info.groupOwnerAddress.toString();
                                    new ClientAsyncTask(mContext).execute(ownerAddr);
//                                    ClientAsyncTask cat = new ClientAsyncTask(mContext);
//                                    cat.doInBackground(ownerAddr);
//                                    cat.execute();
                                }
                            }
                        });
                        Log.d(TAG, "Connected!");
                        Toast.makeText(mContext, "Connected!", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            }
        }
    }

    static class ServerAsyncTask extends AsyncTask<Void, Void, Void> {
        private Context context;
        //Constructor
        public ServerAsyncTask(Context context) {
            this.context = context;
        }
        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Server: Running background task...");
            try {
                // Create a server socket & wait for client connection. This is done in the
                // background in an asynchronous task b/c it is blocking. It listens until it connects.
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                Socket clientSocket = serverSocket.accept(); // blocks until a client connects
                Log.d(TAG, "Sockets connected");

                // Connection is established when it reaches this part of the code (b/c the previous
                // call was blocking)
                String msg_received = null;
                while(msg_received == null) {
                    DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                    msg_received = dis.readUTF();
                }
                Log.d(TAG, "Message received: " + msg_received);
                Toast.makeText(context, "Message received: " + msg_received, Toast.LENGTH_LONG).show();
                clientSocket.close();
                serverSocket.close();
            } catch(IOException e) {
                Log.d(TAG, e.getMessage());
                return null;
            }
            Log.d(TAG, "Server: Return from background task.");
            return null;
        }
    }

    static class ClientAsyncTask extends AsyncTask<String, Void, Void> {
        private Context context;
        //Constructor
        public ClientAsyncTask(Context context) {
            this.context = context;
        }

        protected Void doInBackground(String params) {
            Log.d(TAG, "Client: Running background task...");
            Socket socket = new Socket();
            DataOutputStream dos = null;
            try {
                wait(3000); // Delay to give server time to create sockets & wait for connection
                socket.bind(null);
                socket.connect((new InetSocketAddress(params, SERVER_PORT)), 500);
                dos = new DataOutputStream(socket.getOutputStream());
                // Send a message through the socket
                dos.writeUTF("HELLO FROM THE OTHER SIDE!");
                socket.close();
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Client: Return from background task.");
            return null;
        }

        @Override
        protected Void doInBackground(String... params) {
            return null;
        }
    }
}
