package edu.wpi.activityrecognition;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private MapFragment mMapFragment;
    private BroadcastReceiver mReceiver;
    private ImageView mActivityImage;
    private TextView mActivityText;
    private TextView fullerVisitsTextView;
    private TextView libraryVisitsTextView;
    private long timeActivityStarted = 0;
    private String currentActivity;

    //Geofences
    private List<Geofence> mGeofenceList;
    private PendingIntent mGeofenceRequestIntent;
    private Geofence fullerGeofence;
    private Geofence libraryGeofence;
    private int fullerVisists = 0;
    private int libraryVisits = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the google api client
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .build();
            mGoogleApiClient.connect();
        }

        // Set up the map
        mMapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        mActivityImage = (ImageView) findViewById(R.id.imageView);
        mActivityText = (TextView) findViewById(R.id.textActivity);
        fullerVisitsTextView = (TextView) findViewById(R.id.fullerVisitText);
        libraryVisitsTextView = (TextView) findViewById(R.id.libraryVisitText);

        //Set up geofence
        mGeofenceList = new ArrayList<>();
        createGeofences();

        // Listen for LocalBroadcast of activity recognition
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activity = intent.getStringExtra(ActivityRecognizedService.LOCAL_BROADCAST_EXTRA);
                if (activity.equals("STILL") || activity.equals("RUNNING") || activity.equals("WALKING")){
                    processActivity(activity);
                } else {
                    processGeoActivity(activity);
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(ActivityRecognizedService.LOCAL_BROADCAST_NAME));
    }

    public void createGeofences() {
        //Make the fuller geofence
        fullerGeofence = new Geofence.Builder()
                .setRequestId("fuller")
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setCircularRegion(42.275076, -71.806496, 40f)
                .setExpirationDuration(3600000)
                .build();

        //Make the library geofence
        libraryGeofence = new Geofence.Builder()
                .setRequestId("library")
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setCircularRegion(42.274264, -71.806251, 40f)
                .setExpirationDuration(3600000)
                .build();

        //Adds them to the list of geofences
        mGeofenceList.add(fullerGeofence);
        mGeofenceList.add(libraryGeofence);
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
    }

    // google api is connected
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // start activity recognition
        Intent intent = new Intent(this, ActivityRecognizedService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0, pendingIntent);

        // start geofence
        mGeofenceRequestIntent = getGeofenceTransitionPendingIntent();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, mGeofenceList,
                mGeofenceRequestIntent);
    }

    /**
     * Create a PendingIntent that triggers GeofenceTransitionIntentService when a geofence
     * transition occurs.
     */
    private PendingIntent getGeofenceTransitionPendingIntent() {
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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
                    break;
            }
        }

    }

    //TODO: Do the step counter thing
    //This is where you decide what happens to geofence activity
    private void processGeoActivity(String activity) {
        if (activity.equals("entering fuller")){
            //start counting steps

            //If we reach 6 steps...
            //make toast
            Toast.makeText(this, "You have taken 6 steps inside Fuller Laboratories Geofence, incrementing counter", Toast.LENGTH_SHORT).show();
            //change ui
            fullerVisists++;
            fullerVisitsTextView.setText(fullerVisists);



        }
        if (activity.equals("entering library")){


            Toast.makeText(this, "You have taken 6 steps inside the Gordon Library Geofence, incrementing counter", Toast.LENGTH_SHORT).show();
            libraryVisits++;
            libraryVisitsTextView.setText(libraryVisits);

        }
    }
}
