package edu.wpi.activityrecognition;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;

/**
 * Created by Kevin O'Brien on 2/8/2018.
 */

public class GeofenceTransitionService extends IntentService {

    private static final String TAG = GeofenceTransitionService.class.getSimpleName();

    public static String LOCAL_BROADCAST_NAME = "LOCAL_GEOFENCE";
    public static String LOCAL_BROADCAST_EXTRA_FENCE = "RESULTFENCE";
    public static String LOCAL_BROADCAST_EXTRA_TRANSITION = "RESULTTRANSITION";

    public GeofenceTransitionService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String transition = "";
        String fence = "";
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (geofencingEvent.hasError()) {
            String errorMsg = getErrorString(geofencingEvent.getErrorCode());
            Log.e(TAG, errorMsg);
            return;
        }
        Log.d(TAG, "this works");

        int geoFenceTransition = geofencingEvent.getGeofenceTransition();

        // Check if the transition type is of interest

        if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            transition = "ENTER";
            fence = geofencingEvent.getTriggeringGeofences().get(0).getRequestId();
            Log.d(TAG, "We entered the geofence" + fence);
        } else if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            transition = "EXIT";
            fence = geofencingEvent.getTriggeringGeofences().get(0).getRequestId();
            Log.d(TAG, "we exited the geofence" + fence);
        }
/*
        Intent broadcastIntent = new Intent(LOCAL_BROADCAST_NAME);
        broadcastIntent.putExtra(LOCAL_BROADCAST_EXTRA_FENCE, fence);
        broadcastIntent.putExtra(LOCAL_BROADCAST_EXTRA_TRANSITION, transition);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);*/

    }


    private static String getErrorString(int errorCode) {
        switch (errorCode) {
            case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
                return "Geofence not available";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                return "too many geofences";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                return "Too many pending intents";
            default:
                return "Unknown error";
        }
    }
}
