package info.devexchanges.googleplayservicesnearbyapi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Connections;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ConstantConditions")
@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        Connections.ConnectionRequestListener, Connections.MessageListener,
        GoogleApiClient.ConnectionCallbacks,
        Connections.EndpointDiscoveryListener, View.OnClickListener {

    private String mRemoteHostEndpoint;
    private GoogleApiClient googleApiClient;
    private ListView listView;
    private View btnConnect;
    private View btnSend;
    private ArrayAdapter<String> adapter;
    private List<String> remotePeerEndpoints;
    private boolean isConnected;
    private TextInputLayout textInputLayout;
    private View inputLayout;
    private TextView status;
    private RadioGroup radioGroup;
    private boolean isHost = false;

    private long CONNECTION_TIME_OUT = 15000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        remotePeerEndpoints = new ArrayList<>();

        listView = (ListView) findViewById(R.id.list);
        btnConnect = findViewById(R.id.btn_connect);
        btnSend = findViewById(R.id.btn_send);
        inputLayout = findViewById(R.id.input_layout);
        radioGroup = (RadioGroup) findViewById(R.id.radio_group);
        status = (TextView) findViewById(R.id.txt_status);
        textInputLayout = (TextInputLayout) findViewById(R.id.input);

        btnSend.setOnClickListener(this);
        btnConnect.setOnClickListener(this);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, remotePeerEndpoints);
        listView.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            Nearby.Connections.stopAdvertising(googleApiClient);
            googleApiClient.disconnect();
        }
    }

    public boolean isConnectedToNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetwork != null && wifiNetwork.isConnected()) {
            return true;
        }

        NetworkInfo mobileNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mobileNetwork != null && mobileNetwork.isConnected()) {
            return true;
        }

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            return true;
        }

        return false;
    }

    private void advertise() {
        if (!isConnectedToNetwork()) return;

        String name = "Nearby Advertising";
        Nearby.Connections.startAdvertising(googleApiClient, name, null, CONNECTION_TIME_OUT, this)
                .setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
                    @Override
                    public void onResult(Connections.StartAdvertisingResult result) {
                        if (result.getStatus().isSuccess()) {
                            status.setText("Advertising");
                        }
                    }
                });
    }

    private void disconnect() {
        if (!isConnectedToNetwork())
            return;

        if (isHost) {
            sendMessage("Shutting down host");
            Nearby.Connections.stopAdvertising(googleApiClient);
            Nearby.Connections.stopAllEndpoints(googleApiClient);
            isHost = false;
            status.setText("Not connected");
            remotePeerEndpoints.clear();
        } else {
            if (!isConnected || TextUtils.isEmpty(mRemoteHostEndpoint)) {
                Nearby.Connections.stopDiscovery(googleApiClient, getString(R.string.service_id));
                return;
            }

            sendMessage("Disconnecting");
            Nearby.Connections.disconnectFromEndpoint(googleApiClient, mRemoteHostEndpoint);
            mRemoteHostEndpoint = null;
            status.setText("Disconnected");
        }

        isConnected = false;
    }

    private void sendMessage(String message) {
        if (isHost) {
            Nearby.Connections.sendReliableMessage(googleApiClient, remotePeerEndpoints, message.getBytes());
            adapter.add(message);
            adapter.notifyDataSetChanged();
        } else {
            Nearby.Connections.sendReliableMessage(googleApiClient, mRemoteHostEndpoint,
                    (Nearby.Connections.getLocalDeviceId(googleApiClient) + " says: " + message).getBytes());
        }
    }

    @Override
    public void onConnectionRequest(final String remoteEndpointId, final String remoteDeviceId,
                                    final String remoteEndpointName, byte[] payload) {
        if (isHost) {
            Nearby.Connections.acceptConnectionRequest(googleApiClient, remoteEndpointId, payload, this)
                    .setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                if (!remotePeerEndpoints.contains(remoteEndpointId)) {
                                    remotePeerEndpoints.add(remoteEndpointId);
                                }

                                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                adapter.notifyDataSetChanged();
                                sendMessage(remoteDeviceId + " connected!");
                                inputLayout.setVisibility(View.VISIBLE);
                            }
                        }
                    });
        } else {
            Nearby.Connections.rejectConnectionRequest(googleApiClient, remoteEndpointId);
        }
    }

    private void discover() {
        if (!isConnectedToNetwork())
            return;

        String serviceId = getString(R.string.service_id);
        Nearby.Connections.startDiscovery(googleApiClient, serviceId, CONNECTION_TIME_OUT, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            MainActivity.this.status.setText("Discovering");
                        }
                    }
                });
    }

    @Override
    public void onEndpointFound(String endpointId, String deviceId, final String serviceId, String endpointName) {
        byte[] payload = null;

        Nearby.Connections.sendConnectionRequest(googleApiClient, deviceId, endpointId, payload,
                new Connections.ConnectionResponseCallback() {

                    @Override
                    public void onConnectionResponse(String s, Status status, byte[] bytes) {
                        if (status.isSuccess()) {
                            MainActivity.this.status.setText("Connected to: " + s);
                            Nearby.Connections.stopDiscovery(googleApiClient, serviceId);
                            mRemoteHostEndpoint = s;
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            inputLayout.setVisibility(View.VISIBLE);

                            if (!isHost) {
                                isConnected = true;
                            }
                        } else {
                            MainActivity.this.status.setText("Connection to " + s + " failed");
                            if (!isHost) {
                                isConnected = false;
                            }
                        }
                    }
                }, this);
    }

    @Override
    public void onEndpointLost(String s) {
        if (!isHost) {
            isConnected = false;
        }
    }

    @Override
    public void onMessageReceived(String s, byte[] bytes, boolean b) {
        adapter.add(new String(bytes));
        adapter.notifyDataSetChanged();

        if (isHost) {
            sendMessage(new String(bytes));
        }
    }

    @Override
    public void onDisconnected(String s) {
        if (!isHost) {
            isConnected = false;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_connect: {
                if (isConnected) {
                    disconnect();
                    status.setText("Disconnected");
                } else if (radioGroup.getCheckedRadioButtonId() == R.id.host) {
                    isHost = true;
                    advertise();
                } else {
                    isHost = false;
                    discover();
                }
                break;
            }
            case R.id.btn_send: {
                if (!TextUtils.isEmpty(textInputLayout.getEditText().getText()) && isConnected
                        || (remotePeerEndpoints != null && !remotePeerEndpoints.isEmpty())) {
                    sendMessage(textInputLayout.getEditText().getText().toString());
                    textInputLayout.getEditText().setText("");
                }
                break;
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (!isHost) {
            isConnected = false;
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
