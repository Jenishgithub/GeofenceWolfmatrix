package trackdriver.wm.com.fleet1demo;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import trackdriver.wm.com.fleet1demo.adapter.BookingsAdapter;
import trackdriver.wm.com.fleet1demo.model.Bookings;

/**
 * how run this app in real device
 * - turn gps on
 * - run your app
 * - run lockito app (this app emulates the gps and provides fake gps co-ordinates)
 * - start tracking
 */

public class BookingsActiviy extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status> {

    private RecyclerView rvBookingsList;
    private BookingsAdapter bookingsAdapter;
    private List<Bookings> bookings;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";
    private final int REQ_PERMISSION = 999;

    private String geofenceMessage;

    TextView textViewGeofenceLogs;
    private GoogleApiClient googleApiClient;
    private List<Geofence> mGeofenceList;

    private PendingIntent geoFencePendingIntent;
    private final int GEOFENCE_REQ_CODE = 0;

    private LocationRequest locationRequest;
    // Defined in mili seconds.
    // This number in extremely low, and should be used only for debug
    private final int UPDATE_INTERVAL = 60000;
    private final int FASTEST_INTERVAL = 50000;

    private BroadcastReceiver mbroadCastReceiver;
    StringBuilder stringBuilder;

    public LatLng[] location_1 = new LatLng[]{  //load
            new LatLng(27.68471, 85.30282),
            new LatLng(27.682820, 85.305442),
            new LatLng(27.68142, 85.30852),
            new LatLng(27.682720, 85.311437)};

    public LatLng[] location_2 = new LatLng[]{ //tempo
            new LatLng(27.68034, 85.30216),
            new LatLng(27.67333, 85.30274),
            new LatLng(27.66168, 85.31725),
            new LatLng(27.66664, 85.33246),
            new LatLng(27.67647, 85.34948)};

    public LatLng[] location_3 = new LatLng[]{//my route
            new LatLng(27.68451, 85.30307),
            new LatLng(27.6838, 85.30397),
            new LatLng(27.68095, 85.30953),
            new LatLng(27.68325, 85.31167)};

    private LatLng[] location_4 = new LatLng[]{//circular
            new LatLng(27.68227, 85.31128),
            new LatLng(27.68351, 85.31194),
            new LatLng(27.68366, 85.31265),
            new LatLng(27.68304, 85.31327),
            new LatLng(27.68217, 85.31295),
            new LatLng(27.68127, 85.31228)};


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookings_activity);
        bookings = new ArrayList<>();
        mGeofenceList = new ArrayList<>();
        initializeView();
        createGoogleApi();
        initializeBroadCastReceiver();
        stringBuilder = new StringBuilder("");
    }

    private void initializeView() {

        rvBookingsList = findViewById(R.id.rv_bookings_list);
        textViewGeofenceLogs = findViewById(R.id.tv_geofence_logs);

        populateBookingsData();
        bookingsAdapter = new BookingsAdapter(this);
        bookingsAdapter.setBookings(bookings);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        rvBookingsList.setLayoutManager(mLayoutManager);
        rvBookingsList.setItemAnimator(new DefaultItemAnimator());
        rvBookingsList.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        rvBookingsList.setAdapter(bookingsAdapter);

        bookingsAdapter.setOnItemClickListener(new BookingsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BookingsAdapter.ItemHolder item, int position, View view) {

                switch (view.getId()) {
                    case R.id.btn_start_tracking:
                        //create geofence
                        clearGeofence();
                        for (int i = 0; i < bookings.get(position).getLatlngs().size(); i++) {
                            createGeofences(bookings.get(position).getLatlngs().get(i).latitude,
                                    bookings.get(position).getLatlngs().get(i).longitude, i);
                        }
                        addGeofences();

                        break;

                    case R.id.btn_stop_tracking:
                        clearGeofence();
                        clearAllLogs();
                        break;

                }
            }

        });
    }

    private void clearAllLogs() {
        textViewGeofenceLogs.setText("");
        geofenceMessage = "";
        stringBuilder = new StringBuilder();
    }

    private void initializeBroadCastReceiver() {
        mbroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(GeofenceTransitionDetails.PACKAGE_NAME)) {
                    geofenceMessage = intent.getExtras().getString(GeofenceTransitionDetails.GEOFENCE_UPDATE);
                    stringBuilder.append("\n");
                    stringBuilder.append(geofenceMessage);
                    textViewGeofenceLogs.setText(stringBuilder.toString());
                }
            }
        };
    }

    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    // Create GoogleApiClient instance
    private void createGoogleApi() {
        Log.d(TAG, "createGoogleApi()");
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    // Check for permission to access Location
    private boolean checkPermission() {
        Log.d(TAG, "checkPermission()");
        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    // Asks for permission
    private void askPermission() {
        Log.d(TAG, "askPermission()");
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_PERMISSION
        );
    }

    // Verify user's response of the permission requested
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted


                } else {
                    // Permission denied
                    permissionsDenied();
                }
                break;
            }
        }
    }


    // App cannot work without the permissions
    private void permissionsDenied() {
        Log.w(TAG, "permissionsDenied()");
        // TODO close app and warn user
    }


    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GeofenceTransitionDetails.PACKAGE_NAME);
        registerReceiver(mbroadCastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mbroadCastReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Call GoogleApiClient connection when starting the Activity
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Disconnect GoogleApiClient when stopping Activity
        googleApiClient.disconnect();
    }

    /**
     * Create a Geofence list
     */
    public void createGeofences(double latitude, double longitude, int geofenceNumber) {
        Geofence.Builder builder = new Geofence.Builder();
        builder.setRequestId(String.valueOf(geofenceNumber));
        builder.setCircularRegion(latitude, longitude, 200);//might use 100m radius for more accurate results
        builder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        if (geofenceNumber == 0 || geofenceNumber == 1 || geofenceNumber == 2 || geofenceNumber == 3) {
            builder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL
                    | Geofence.GEOFENCE_TRANSITION_EXIT);//If GEOFENCE_TRANSITION_ENTER is also specified,
            // this alert will always be sent after the GEOFENCE_TRANSITION_ENTER alert.
            builder.setLoiteringDelay(15000);//30sec dwell time
            /*Sets the delay between GEOFENCE_TRANSITION_ENTER and GEOFENCE_TRANSITION_DWELLING in milliseconds.
            For example, if loitering delay is set to 300000 ms (i.e. 5 minutes) the geofence service
             will send a GEOFENCE_TRANSITION_DWELL alert roughly 5 minutes after user enters a geofence if the
              user stays inside the geofence during this period of time. If the user exits from the geofence in
              this amount of time, GEOFENCE_TRANSITION_DWELL alert won't be sent**/
        } else
            builder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
        Geofence fence = builder.build();
        mGeofenceList.add(fence);
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    private void addGeofences() {
        Log.d(TAG, "addGeofences");
        if (checkPermission()) {
            LocationServices.GeofencingApi.addGeofences(
                    googleApiClient,
                    getGeofencingRequest(),
                    createGeofencePendingIntent()
            ).setResultCallback(this);
        }
    }

    private PendingIntent createGeofencePendingIntent() {
        Log.d(TAG, "createGeofencePendingIntent");
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(this, GeofenceTransitionDetails.class);
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
        if (status.isSuccess()) {
            Log.i(TAG, "onResult: success");
        } else {
            Log.i(TAG, "onResult: failure");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged");
    }

    @Override
    public void onMapClick(LatLng latLng) {

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

    }

    // Clear Geofence
    private void clearGeofence() {
        Log.d(TAG, "clearGeofence()");
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent()
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    // remove drawing
                }
            }
        });
    }

    private void populateBookingsData() {
        Bookings bookings_1 = new Bookings();
        bookings_1.setName("Booking1");
        bookings_1.setLatlngs(new ArrayList<>(Arrays.asList(location_1)));
        bookings.add(bookings_1);
        Bookings bookings_2 = new Bookings();
        bookings_2.setName("Booking2");
        bookings_2.setLatlngs(new ArrayList<>(Arrays.asList(location_2)));
        bookings.add(bookings_2);
        Bookings bookings_3 = new Bookings();
        bookings_3.setName("Booking3");
        bookings_3.setLatlngs(new ArrayList<>(Arrays.asList(location_3)));
        bookings.add(bookings_3);
        Bookings bookings_4 = new Bookings();
        bookings_4.setName("Booking4");
        bookings_4.setLatlngs(new ArrayList<>(Arrays.asList(location_4)));
        bookings.add(bookings_4);
    }


}
