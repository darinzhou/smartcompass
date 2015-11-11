package com.comcast.smartcompass;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;


/**
 * Utility class that provides bearing values to true north.
 */
public class BearingToNorthProvider implements SensorEventListener, LocationListener {
    public static final String TAG = "BearingToNorthProvider";

    /**
     * Interface definition for a callback to be invoked when the bearing changes.
     */
    public static interface ChangeEventListener {
        /**
         * Callback method to be invoked when the bearing changes.
         *
         * @param bearing the new bearing value
         */
        void onBearingChanged(double bearing);

        void onLocationChanged(Location location);
    }

    private final SensorManager mSensorManager;
    private final LocationManager mLocationManager;
    private final Sensor mSensorAccelerometer;
    private final Sensor mSensorMagneticField;

    // some arrays holding intermediate values read from the sensors, used to calculate our azimuth
    // value

    private float[] mValuesAccelerometer;
    private float[] mValuesMagneticField;
    private float[] mMatrixR;
    private float[] mMatrixI;
    private float[] mMatrixValues;

    /**
     * minimum change of bearing (degrees) to notify the change listener
     */
    private final double mMinDiffForEvent;

    /**
     * minimum delay (millis) between notifications for the change listener
     */
    private final double mThrottleTime;

    /**
     * the change event listener
     */
    private ChangeEventListener mChangeEventListener;

    /**
     * angle to magnetic north
     */
    private AverageAngle mAzimuthRadians;

    /**
     * smoothed angle to magnetic north
     */
    private double mAzimuth = Double.NaN;

    /**
     * angle to true north
     */
    private double mBearing = Double.NaN;

    /**
     * last notified angle to true north
     */
    private double mLastBearing = Double.NaN;

    /**
     * Current GPS/WiFi location
     */
    private Location mLocation;

    /**
     * when we last dispatched the change event
     */
    private long mLastChangeDispatchedAt = -1;

    /**
     * Default constructor.
     *
     * @param context Application Context
     */
    public BearingToNorthProvider(Context context) {
        this(context, 10, 1.0, 1000);
    }

    /**
     * @param context         Application Context
     * @param smoothing       the number of measurements used to calculate a mean for the azimuth. Set
     *                        this to 1 for the smallest delay. Setting it to 5-10 to prevents the
     *                        needle from going crazy
     * @param minDiffForEvent minimum change of bearing (degrees) to notify the change listener
     * @param throttleTime    minimum delay (millis) between notifications for the change listener
     */
    public BearingToNorthProvider(Context context, int smoothing, double minDiffForEvent, int throttleTime) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mSensorMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mValuesAccelerometer = new float[3];
        mValuesMagneticField = new float[3];

        mMatrixR = new float[9];
        mMatrixI = new float[9];
        mMatrixValues = new float[3];

        mMinDiffForEvent = minDiffForEvent;
        mThrottleTime = throttleTime;

        mAzimuthRadians = new AverageAngle(smoothing);
    }

    //==============================================================================================
    // Public API
    //==============================================================================================

    /**
     * Call this method to start bearing updates.
     */
    public void start() {
        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorMagneticField, SensorManager.SENSOR_DELAY_UI);

        for (final String provider : mLocationManager.getProviders(true)) {
            if (LocationManager.GPS_PROVIDER.equals(provider)
                    || LocationManager.PASSIVE_PROVIDER.equals(provider)
                    || LocationManager.NETWORK_PROVIDER.equals(provider)) {
                if (mLocation == null) {
                    mLocation = mLocationManager.getLastKnownLocation(provider);
                }
                mLocationManager.requestLocationUpdates(provider, 0, 100.0f, this);
            }
        }
    }

    /**
     * call this method to stop bearing updates.
     */
    public void stop() {
        mSensorManager.unregisterListener(this, mSensorAccelerometer);
        mSensorManager.unregisterListener(this, mSensorMagneticField);
        mLocationManager.removeUpdates(this);
    }

    /**
     * @return current bearing
     */
    public double getBearing() {
        return mBearing;
    }

    /**
     * Returns the bearing event listener to which bearing events must be sent.
     *
     * @return the bearing event listener
     */
    public ChangeEventListener getChangeEventListener() {
        return mChangeEventListener;
    }

    /**
     * Specifies the bearing event listener to which bearing events must be sent.
     *
     * @param changeEventListener the bearing event listener
     */
    public void setChangeEventListener(ChangeEventListener changeEventListener) {
        this.mChangeEventListener = changeEventListener;
    }

    //==============================================================================================
    // SensorEventListener implementation
    //==============================================================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        // use low filter to make the bearing smooth
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
//                System.arraycopy(event.values, 0, mValuesAccelerometer, 0, 3);
                mValuesAccelerometer = lowPass( event.values.clone(), mValuesAccelerometer );
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
//                System.arraycopy(event.values, 0, mValuesMagneticField, 0, 3);
                mValuesMagneticField = lowPass( event.values.clone(), mValuesMagneticField );
                break;
        }

        if (mValuesMagneticField!=null && mValuesAccelerometer!=null) {
            boolean success = SensorManager.getRotationMatrix(mMatrixR, mMatrixI,
                    mValuesAccelerometer,
                    mValuesMagneticField);

            // calculate a new smoothed azimuth value and store to mAzimuth
            if (success) {
                SensorManager.getOrientation(mMatrixR, mMatrixValues);
                mAzimuthRadians.putValue(mMatrixValues[0]);
                mAzimuth = Math.toDegrees(mAzimuthRadians.getAverage());
            }

            // update mBearing
            updateBearing();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    //==============================================================================================
    // LocationListener implementation
    //==============================================================================================

    @Override
    public void onLocationChanged(Location location) {
        // set the new location
        this.mLocation = location;

        // update mBearing
        updateBearing();

        // update location
        if (mChangeEventListener != null) {
            mChangeEventListener.onLocationChanged(location);
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }

    //==============================================================================================
    // Private Utilities
    //==============================================================================================

    private void updateBearing() {
        if (!Double.isNaN(this.mAzimuth)) {
            if (this.mLocation == null) {
                Log.w(TAG, "Location is NULL bearing is not true north!");
                mBearing = mAzimuth;
            } else {
                mBearing = getBearingForLocation(this.mLocation);
            }

            // Throttle dispatching based on mThrottleTime and minDiffForEvent
            if (System.currentTimeMillis() - mLastChangeDispatchedAt > mThrottleTime &&
                    (Double.isNaN(mLastBearing) || Math.abs(mLastBearing - mBearing) >= mMinDiffForEvent)) {
                mLastBearing = mBearing;
                if (mChangeEventListener != null) {
                    mChangeEventListener.onBearingChanged(mBearing);
                }
                mLastChangeDispatchedAt = System.currentTimeMillis();
            }
        }
    }

    private double getBearingForLocation(Location location) {
        float declination = getGeomagneticField(location).getDeclination();
        Log.d(TAG, "--- Azimuth: " + mAzimuth);
        Log.d(TAG, "--- GeomagneticField-Declination: " + declination);
        return mAzimuth + declination;
    }

    private GeomagneticField getGeomagneticField(Location location) {
        GeomagneticField geomagneticField = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                System.currentTimeMillis());
        return geomagneticField;
    }

    /*
     * time smoothing constant for low-pass filter
     * 0 ≤ alpha ≤ 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     */
    static final float ALPHA = 0.15f;

    /**
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
     * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
     */
    public float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;

        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }
}