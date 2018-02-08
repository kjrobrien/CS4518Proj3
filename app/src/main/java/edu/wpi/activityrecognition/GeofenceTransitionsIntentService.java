package edu.wpi.activityrecognition;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

/**
 * Listens for geofence transition changes.
 */
public class GeofenceTransitionsIntentService extends IntentService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static String LOCAL_BROADCAST_NAME = "LOCAL_GEO_RECOGNITION";
    public static String LOCAL_BROADCAST_EXTRA = "GEO_RESULT";

    public GeofenceTransitionsIntentService() {
        super(GeofenceTransitionsIntentService.class.getSimpleName());
    }

    /**
     * Handles incoming intents.
     * @param intent The Intent sent by Location Services. This Intent is provided to Location
     * Services (inside a PendingIntent) when addGeofences() is called.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geoFenceEvent = GeofencingEvent.fromIntent(intent);
        if (geoFenceEvent.hasError()) {
            int errorCode = geoFenceEvent.getErrorCode();
            Log.e("Geofencing", "Location Services error: " + errorCode);
        } else {
            handleEvent(geoFenceEvent);
        }
    }

    //Handles the event og a geoFence.
    public void handleEvent(GeofencingEvent event){
        String message = "Incorrect event???"; //If we get this, some weird shit happened

        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER){
            Log.e("GEOFENCE!!!!", "ENTERING " + event.getTriggeringGeofences().get(0).getRequestId());
            message = "entering " + event.getTriggeringGeofences().get(0).getRequestId();
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT){
            Log.e("GEOFENCE!!!!", "LEAVING " + event.getTriggeringGeofences().get(0).getRequestId());
            message = "leaving " + event.getTriggeringGeofences().get(0).getRequestId();
        }
        Intent intent = new Intent(LOCAL_BROADCAST_NAME);
        intent.putExtra(LOCAL_BROADCAST_EXTRA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }
}
