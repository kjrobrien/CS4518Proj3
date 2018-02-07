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
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    private MapFragment mMapFragment;

    private BroadcastReceiver mReceiver;

    private ImageView mActivityImage;
    private TextView mActivityText;

    private long timeActivityStarted = 0;

    private String currentActivity;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the google api client
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).addApi(ActivityRecognition.API).build();
            mGoogleApiClient.connect();
        }

        // Set up the map
        mMapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        mActivityImage = (ImageView) findViewById(R.id.imageView);
        mActivityText = (TextView) findViewById(R.id.textActivity);

        // Listen for LocalBroadcast of activity recognition
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activity = intent.getStringExtra(ActivityRecognizedService.LOCAL_BROADCAST_EXTRA);
                processActivity(activity);
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(ActivityRecognizedService.LOCAL_BROADCAST_NAME));
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
        Intent intent = new Intent( this, ActivityRecognizedService.class );
        PendingIntent pendingIntent = PendingIntent.getService( this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mGoogleApiClient, 0, pendingIntent );
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
}
