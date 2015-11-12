package com.comcast.compass;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BearingToNorthProvider.ChangeEventListener,
        MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks {

    public static final String TAG = "MainActivity";

    public static final String ZOOM_KEY = "zoom";
    public static final float ZOOM_DEFAULT = 18f;

    public static final String MODE_KEY = "mode";
    public static final int MODE_COMPASS = 0;
    public static final int MODE_MAP = 1;

    public static final String MSG_START = "/msg_start";
    public static final String MSG_LATLNG = "/msg_latlng";
    public static final String MSG_ADDRESS = "/msg_address";

    private View mViewCompass;
    private View mViewMap;
    private TextView mTvTitle;
    private Button mBtSwitch;
    private TextView mTvLatLng;
    private TextView mTvAddress;

    private GoogleMap mMap;
    private LatLng mCurrentLatLng;
    private float mZoom;

    private boolean mShowCompass;

    private ImageView mIvCompass;
    private BearingToNorthProvider mBearingProvider;
    private float mCurrentDegree = 0f;

    private SharedPreferences mPref;

    private GoogleApiClient mApiClient;

    private String mAddress;

    private void switchViews() {
        mShowCompass = !mShowCompass;
        showSpecifiedView();
    }

    private void showSpecifiedView() {
        if (mShowCompass) {
            mBtSwitch.setText(getResources().getString(R.string.title_map));
            mViewCompass.setVisibility(View.VISIBLE);
            mViewMap.setVisibility(View.GONE);
        } else {
            mBtSwitch.setText(getResources().getString(R.string.title_compass));
            mViewCompass.setVisibility(View.GONE);
            mViewMap.setVisibility(View.VISIBLE);
        }

        SharedPreferences.Editor editor = mPref.edit();
        editor.putInt(MODE_KEY, mShowCompass ? MODE_COMPASS : MODE_MAP);
        editor.commit();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPref = PreferenceManager.getDefaultSharedPreferences(this);

        mViewCompass = findViewById(R.id.vCompass);
        mViewMap = findViewById(R.id.vMap);
        mTvTitle = (TextView) findViewById(R.id.tvTitle);
        mBtSwitch = (Button) findViewById(R.id.btSwitch);
        mBtSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchViews();
            }
        });
        mTvLatLng = (TextView) findViewById(R.id.tvLatLng);
        mTvAddress = (TextView) findViewById(R.id.tvAddress);

        // compass
        mIvCompass = (ImageView) findViewById(R.id.ivCompass);
        mBearingProvider = new BearingToNorthProvider(this);
        mBearingProvider.setChangeEventListener(this);

        // map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.fMap);
        mMap = mapFragment.getMap();
//        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        mMap.setMyLocationEnabled(true);
        UiSettings uis = mMap.getUiSettings();
        uis.setZoomControlsEnabled(false);
        uis.setMyLocationButtonEnabled(false);

        mZoom = mPref.getFloat(ZOOM_KEY, ZOOM_DEFAULT);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(mZoom));

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                mZoom = arg0.zoom;

                SharedPreferences.Editor editor = mPref.edit();
                editor.putFloat(ZOOM_KEY, mZoom);
                editor.commit();
            }
        });

        mShowCompass = (mPref.getInt(MODE_KEY, MODE_COMPASS) == MODE_COMPASS);
        showSpecifiedView();

        initGoogleApiClient();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBearingProvider.start();
        if (mApiClient != null && !(mApiClient.isConnected() || mApiClient.isConnecting()))
            mApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBearingProvider.stop();
        if (mApiClient != null) {
            Wearable.MessageApi.removeListener(mApiClient, this);
            if (mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mApiClient != null)
            mApiClient.unregisterConnectionCallbacks(this);
    }

    @Override
    public void onBearingChanged(double bearing) {
        // adjust bearing based on phoe orientation
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        float degree = (float) bearing + rotation * 90;

        float deg = degree;
        if (degree < 0) {
            deg += 360;
        }
        String degreeToDisplay = String.format(" (%.2f" + (char) 0x00B0 + ")", deg);
        String title = getResources().getString(R.string.app_name) + " - " + getDirectionFromDegrees(degree) + degreeToDisplay;
        mTvTitle.setText(title);

        RotateAnimation ra = new RotateAnimation(
                mCurrentDegree, -degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setDuration(250);
        ra.setFillAfter(true);
        mIvCompass.startAnimation(ra);

        mCurrentDegree = -degree;

        if (mCurrentLatLng != null) {
            CameraPosition currentPlace = new CameraPosition.Builder()
                    .target(mCurrentLatLng)
                    .bearing(degree).tilt(65.5f).zoom(mZoom).build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(currentPlace));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(mCurrentLatLng, mZoom);
        mMap.animateCamera(cameraUpdate);

        // update lat/lng
        String latLng = mCurrentLatLng.latitude + ", " + mCurrentLatLng.longitude;
        mTvLatLng.setText(latLng);
//        // send to wear
//        sendMessage(MSG_LATLNG, latLng);

        // get address
        mAddress = getAddressFromLatLng(this, mCurrentLatLng.latitude, mCurrentLatLng.longitude);
        if (mAddress != null) {
            mTvAddress.setText(mAddress);
//            // send to wear
//            sendMessage(MSG_ADDRESS, mAddress);
        }

        // send to wear
        sendMessage(MSG_ADDRESS, latLng + ";" + mAddress);

//        // get address
//        new AsyncTask<Void, Void, String> () {
//            @Override
//            protected String doInBackground(Void... voids) {
//                return GoogleAPI.getAddressFromLatLng(mCurrentLatLng);//getAddressFromLatLng(this, mCurrentLatLng.latitude, mCurrentLatLng.longitude);
//            }
//            @Override
//            protected void onPostExecute(String address) {
//                super.onPostExecute(address);
//                if (address != null) {
//                    mTvAddress.setText(address);
//
//                    // send to wear
//                    sendMessage(MSG_ADDRESS, address);
//                }
//            }
//        }.execute();
    }

    public static String getDirectionFromDegrees(float degrees) {
        if (degrees >= -11.25 && degrees < 11.25) {
            return "N";
        }
        if (degrees >= 11.25 && degrees < 33.75) {
            return "nNE";
        }
        if (degrees >= 33.75 && degrees < 56.25) {
            return "NE";
        }
        if (degrees >= 56.25 && degrees < 78.75) {
            return "eNE";
        }
        if (degrees >= 78.75 && degrees < 101.25) {
            return "E";
        }
        if (degrees >= 101.25 && degrees < 123.75) {
            return "eSE";
        }
        if (degrees >= 123.75 && degrees < 146.25) {
            return "SE";
        }
        if (degrees >= 146.25 && degrees < 168.75) {
            return "sSE";
        }
        if (degrees >= 168.75 || degrees < -168.75) {
            return "S";
        }
        if (degrees >= -168.75 && degrees < -146.25) {
            return "sSW";
        }
        if (degrees >= -146.25 && degrees < -123.75) {
            return "SW";
        }
        if (degrees >= -123.75 && degrees < -101.25) {
            return "wSW";
        }
        if (degrees >= -101.25 && degrees < -78.75) {
            return "W";
        }
        if (degrees >= -78.75 && degrees < -56.25) {
            return "wNW";
        }
        if (degrees >= -56.25 && degrees < -33.75) {
            return "NW";
        }
        if (degrees >= -33.75 && degrees < -11.25) {
            return "nNW";
        }

        return null;
    }

    public static String getAddressFromLatLng(Context context, double lat, double lng) {
        String address = null;
        Geocoder coder = new Geocoder(context);

        try {
            List<Address> addresses = coder.getFromLocation(lat, lng, 5);
            if (addresses == null || addresses.size() == 0)
                return null;
            Address location = addresses.get(0);
            if (location.getMaxAddressLineIndex() == 0)
                address = location.getAddressLine(0);
            else if (location.getMaxAddressLineIndex() > 0)
                address = location.getAddressLine(0) + ", " + location.getAddressLine(1);

        } catch (IOException e) {
            // we know there a lot issues with android Geocoder class, just swallow these exceptions
//            e.printStackTrace();
        }

        return address;
    }

    //
    // communication with wear
    //

    private void initGoogleApiClient() {
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        if (mApiClient != null && !(mApiClient.isConnected() || mApiClient.isConnecting()))
            mApiClient.connect();    }

    private void sendMessage(final String path, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await();
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mApiClient, node.getId(), path, text.getBytes()).await();
                }
            }
        }).start();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(mApiClient, this);
        sendMessage(MSG_START, "");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equalsIgnoreCase(MSG_ADDRESS)) {
            if (mCurrentLatLng != null) {
                String latLng = mCurrentLatLng.latitude + ", " + mCurrentLatLng.longitude;
                sendMessage(MSG_ADDRESS, latLng + ";" + mAddress);
            }
        }
    }
}

