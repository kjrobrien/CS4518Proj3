package edu.wpi.activityrecognition;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback, ResultCallback<Status>, SensorEventListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    private MapFragment mMapFragment;

    private BroadcastReceiver mActivityReceiver;
    private BroadcastReceiver mGeoFenceReceiver;

    private ImageView mActivityImage;
    private TextView mActivityText;

    private long timeActivityStarted = 0;

    private String currentActivity;

    private TextView fullerVisitsTextView;
    private TextView libraryVisitsTextView;

    int fullerVisits = 0;
    int libraryVisits = 0;

    int fullerSteps = 0;
    int librarySteps = 0;

    private boolean inFuller = false;
    private boolean inLibrary = false;

    private SensorManager mSensorManager;
    private Sensor mSensor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the google api client
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).addApi(ActivityRecognition.API).build();
        }

        // Set up the map
        mMapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        mActivityImage = (ImageView) findViewById(R.id.imageView);
        mActivityText = (TextView) findViewById(R.id.textActivity);


        fullerVisitsTextView = (TextView) findViewById(R.id.fullerVisitsText);
        libraryVisitsTextView = (TextView) findViewById(R.id.libraryVisitsText);

        // Listen for LocalBroadcast of activity recognition
        mActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activity = intent.getStringExtra(ActivityRecognizedService.LOCAL_BROADCAST_EXTRA);
                processActivity(activity);
            }
        };

        mGeoFenceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String transition = intent.getStringExtra(GeofenceTransitionService.LOCAL_BROADCAST_EXTRA_TRANSITION);
                String fence = intent.getStringExtra(GeofenceTransitionService.LOCAL_BROADCAST_EXTRA_FENCE);
                processGeofence(transition, fence);
            }
        };

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    private void processGeofence(String transition, String fence) {
        if(transition.equals("ENTER")) {
            if (fence.equals("FULLER")) {

                Toast.makeText(this, "Entered geofence fuller", Toast.LENGTH_SHORT).show();
                inFuller = true;
                fullerSteps = 0;
            } else if (fence.equals("LIBRARY")) {
                Toast.makeText(this, "Entered geofence library", Toast.LENGTH_SHORT).show();

                inLibrary = true;
                librarySteps = 0;
            } else if (fence.equals("APARTMENT")) {
                Toast.makeText(this, "Entered geofence apartment", Toast.LENGTH_SHORT).show();
                inFuller = true;
                fullerSteps = 0;
            }
        } else if (transition.equals("EXIT")) {
            if (fence.equals("FULLER")) {
                Toast.makeText(this, "exited geofence fuller", Toast.LENGTH_SHORT).show();

                inFuller = false;
                fullerSteps = 0;
            } else if (fence.equals("LIBRARY")) {
                Toast.makeText(this, "exited geofence library", Toast.LENGTH_SHORT).show();

                inLibrary = false;
                librarySteps = 0;
            } else if (fence.equals("APARTMENT")) {
                Toast.makeText(this, "Exited geofence apartment", Toast.LENGTH_SHORT).show();
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mActivityReceiver, new IntentFilter(ActivityRecognizedService.LOCAL_BROADCAST_NAME));
        LocalBroadcastManager.getInstance(this).registerReceiver(mGeoFenceReceiver, new IntentFilter(GeofenceTransitionService.LOCAL_BROADCAST_NAME));
        mGoogleApiClient.connect();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    // When the map loads
    @Override
    public void onMapReady(final GoogleMap googleMap) {
        // confirm we have location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        googleMap.setMyLocationEnabled(true); // show the blue dot for our location
        googleMap.getUiSettings().setMyLocationButtonEnabled(false); // disable the find location button

        // find our location and move the map to that location
        LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // Got last known location. In some rare situations this can be null.
                if (location != null) {
                    // Logic to handle location object
                    double lat = location.getLatitude();
                    double longitude = location.getLongitude();
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, longitude), 16));
                }
            }
        });


        CircleOptions myApartment = new CircleOptions().center(new LatLng(42.270330, -71.804551)).radius(40.0f);

        CircleOptions fuller = new CircleOptions().center(new LatLng(42.275060, -71.806504)).radius(40.0f);
        CircleOptions library = new CircleOptions().center(new LatLng(42.274099, -71.806736)).radius(40.0f);

        googleMap.addCircle(fuller);
        googleMap.addCircle(library);
        googleMap.addCircle(myApartment);


    }

    // google api is connected
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // start activity recognition
        Intent intent = new Intent(this, ActivityRecognizedService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0, pendingIntent);

        Geofence geofence = new Geofence.Builder()
                .setRequestId("APARTMENT")
                .setCircularRegion(42.270330, -71.804551, 40.0f)
                .setExpirationDuration(60 * 60 * 1000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        Geofence fullerGeofence = new Geofence.Builder()
                .setRequestId("FULLER")
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setCircularRegion(42.275060, -71.806504, 40f)
                .setExpirationDuration(3600000)
                .build();

        Geofence libraryGeofence = new Geofence.Builder()
                .setRequestId("LIBRARY")
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setCircularRegion(42.274099, -71.806736, 40f)
                .setExpirationDuration(3600000)
                .build();

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .addGeofence(fullerGeofence)
                .addGeofence(libraryGeofence)
                .build();

        Intent geoIntent = new Intent(this, GeofenceTransitionService.class);
        PendingIntent pend = PendingIntent.getService(this, 0, geoIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, geofencingRequest, pend).setResultCallback(this);
    }


    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    private void processActivity(String activity) {
        if (!activity.equals(currentActivity)) {
            if (timeActivityStarted != 0) {
                long timeElapsed = SystemClock.elapsedRealtime() - timeActivityStarted;
                long minutes = timeElapsed / (1000 * 60);
                timeElapsed = timeElapsed % (1000 * 60);
                long seconds = timeElapsed / 1000;
                String pastTense = "";
                if (currentActivity.equals("WALKING")) pastTense = "walked";
                else if (currentActivity.equals("RUNNING")) pastTense = "run";
                else if (currentActivity.equals("STILL")) pastTense = "been still";
                Toast.makeText(this, "You have just " + pastTense + " for " + String.valueOf(minutes) + " minutes and " + String.valueOf(seconds) + " seconds", Toast.LENGTH_SHORT).show();
            }
            timeActivityStarted = SystemClock.elapsedRealtime();
            if (activity.isEmpty()) {
                timeActivityStarted = 0;
            }
            currentActivity = activity;
            Log.d(TAG, "Activity: " + activity);
            switch (activity) {
                case "RUNNING":
                    mActivityImage.setImageResource(R.drawable.running);
                    mActivityText.setText("You are Running");
                    break;
                case "WALKING":
                    mActivityImage.setImageResource(R.drawable.walking);
                    mActivityText.setText("You are Walking");
                    break;
                case "STILL":
                    mActivityImage.setImageResource(R.drawable.still);
                    mActivityText.setText("You are Still");
                    break;
                default:
                    mActivityImage.setImageResource(0);
                    mActivityText.setText("");
                    break;
            }
        }

    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (inFuller) {
            fullerSteps++;
            if (fullerSteps >= 6) {
                fullerVisits++;
                Toast.makeText(this, "You have taken 6 steps inside the Fuller Labs Geofence, incrementing counter", Toast.LENGTH_SHORT).show();
                fullerVisitsTextView.setText(String.valueOf(fullerVisits));
                inFuller = false;
                fullerSteps = 0;
            }
        }
        if (inLibrary) {
            librarySteps++;
            if (librarySteps >= 6) {
                libraryVisits++;
                Toast.makeText(this, "You have taken 6 steps inside the Gordon Library Geofence, incrementing counter", Toast.LENGTH_SHORT).show();
                libraryVisitsTextView.setText(String.valueOf(libraryVisits));
                inLibrary = false;
                librarySteps = 0;
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
