package com.comcast.compass;

import android.location.Location;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;

public class MainActivity extends WearableActivity implements OnMapReadyCallback, MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, BearingToNorthProvider.ChangeEventListener {
    public static final String TAG = "MainActivity";

    public static final String MSG_START = "/msg_start";
    public static final String MSG_LATLNG = "/msg_latlng";
    public static final String MSG_ADDRESS = "/msg_address";

    public static final int FRAGMENT_COMPASS = 0;
    public static final int FRAGMENT_ADDRESS = 1;
    public static final int FRAGMENT_MAP = 2;

//    public static final int MOVE_THRESHOLD = 20;

    public static final float ZOOM_DEFAULT = 17f;

    private GoogleApiClient mApiClient;
    private TextView mTextView;
    private TextView mTvDirection;
    private ImageView mIvCompass;
    private View mViewCompass;
    private View mViewAddress;
    private View mViewMap;
    private View mViewMain;

    private int mFragmentIndex;

//    private float x1, x2;
//    private float y1, y2;

    private BearingToNorthProvider mBearingProvider;
    private float mCurrentDegree = 0f;

    private MapFragment mMapFragment;
    private GoogleMap mMap;
    private LatLng mCurrentLatLng;
    private float mZoom;
    private Marker mMarkerMyLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mViewMain = findViewById(R.id.vMain);
                mViewMain.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        int action = motionEvent.getActionMasked();

                        switch (action) {
                            // when user first touches the screen we get x and y coordinate
                            case MotionEvent.ACTION_DOWN:
//                                x1 = motionEvent.getX();
//                                y1 = motionEvent.getY();
                                Log.d(TAG, "touch down!");

                                mFragmentIndex++;
                                if (mFragmentIndex > FRAGMENT_MAP) {
                                    mFragmentIndex = FRAGMENT_COMPASS;
                                }
                                showSpecifiedFragment();

                                break;

//                            case MotionEvent.ACTION_UP:
//                                x2 = motionEvent.getX();
//                                y2 = motionEvent.getY();
//                                Log.d(TAG, "touch up!");
//
//                                if (Math.abs(x2 - x1) > Math.abs(y2 - y1)) {
//                                    // up and down
//
//                                    if (x2 - x1 > MOVE_THRESHOLD) {
//                                        // up
//                                        Log.d(TAG, "UP!");
//                                    } else if (x1 - x2 > MOVE_THRESHOLD) {
//                                        // down
//                                        Log.d(TAG, "DOWN!");
//                                    }
//                                } else {
//                                    // left and right
//
//                                    if (y2 - y1 > MOVE_THRESHOLD) {
//                                        // right
//                                        Log.d(TAG, "RIGHT!");
//                                    } else if (y1 - y2 > MOVE_THRESHOLD) {
//                                        // left
//                                        Log.d(TAG, "LEFT!");
//                                    }
//                                }
//                                break;
                        }

                        return false;
                    }
                });

                mViewCompass = findViewById(R.id.vCompass);
                mViewAddress = findViewById(R.id.vAddress);
                mViewMap = findViewById(R.id.vMap);

                mTextView = (TextView) stub.findViewById(R.id.text);
                mTvDirection = (TextView) stub.findViewById(R.id.tvDirection);
                mIvCompass = (ImageView) stub.findViewById(R.id.ivCompass);

                // map
                mMapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.fMap);
                mMapFragment.getMapAsync(MainActivity.this);

                // show proper view
                showSpecifiedFragment();
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initGoogleApiClient();

        mBearingProvider = new BearingToNorthProvider(this);
        mBearingProvider.setChangeEventListener(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

//        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
//        mMap.setMyLocationEnabled(true);
        UiSettings uis = mMap.getUiSettings();
        uis.setZoomControlsEnabled(false);
        uis.setMyLocationButtonEnabled(false);

        mZoom = ZOOM_DEFAULT;
        mMap.animateCamera(CameraUpdateFactory.zoomTo(mZoom));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mFragmentIndex = FRAGMENT_COMPASS;
                showSpecifiedFragment();
            }
        });

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                mZoom = arg0.zoom;
                LatLng location = new LatLng(arg0.target.latitude, arg0.target.longitude);

                if (mMarkerMyLocation == null) {
                    mMarkerMyLocation = mMap.addMarker(new MarkerOptions()
                            .position(location)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.my_location)));
                } else {
                    mMarkerMyLocation.setPosition(location);
                }

                mMarkerMyLocation.setRotation(mCurrentDegree);
            }
        });
    }

    private void showSpecifiedFragment() {
        if (mFragmentIndex == FRAGMENT_COMPASS) {
            mViewCompass.setVisibility(View.VISIBLE);
            mViewAddress.setVisibility(View.GONE);
            mViewMap.setVisibility(View.GONE);
        } else if (mFragmentIndex == FRAGMENT_ADDRESS) {
            mViewCompass.setVisibility(View.GONE);
            mViewAddress.setVisibility(View.VISIBLE);
            mViewMap.setVisibility(View.GONE);
        } else if (mFragmentIndex == FRAGMENT_MAP) {
            mViewCompass.setVisibility(View.GONE);
            mViewAddress.setVisibility(View.GONE);
            mViewMap.setVisibility(View.VISIBLE);
        }

    }

    private void initGoogleApiClient() {
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
        if (mApiClient != null && !(mApiClient.isConnected() || mApiClient.isConnecting()))
            mApiClient.connect();
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String msgPath = messageEvent.getPath();
                String received = new String(messageEvent.getData());
                Log.d(TAG, "Received msg: " + received);

                if (msgPath.equalsIgnoreCase(MSG_ADDRESS)) {
                    String[] parts = received.split(";");
                    if (parts.length > 0) {
                        String[] latLng = parts[0].split(",");
                        if (latLng.length == 2) {
                            double lat = Double.valueOf(latLng[0].trim());
                            double lng = Double.valueOf(latLng[1].trim());
                            Location location = new Location("latest_location");
                            location.setLatitude(lat);
                            location.setLongitude(lng);
                            location.setTime(new Date().getTime()); //Set time as current Date

                            onLocationChanged(location);
                        }
                    }
                    if (parts.length == 2) {
                        mTextView.setText(parts[1]);
                    } else if (parts.length == 1) {
                        mTextView.setText(parts[0]);
                    }
                }
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(mApiClient, this);
        sendMessage(MSG_ADDRESS, "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBearingProvider.start();
        if (mApiClient != null && !(mApiClient.isConnected() || mApiClient.isConnecting()))
            mApiClient.connect();
        sendMessage(MSG_ADDRESS, "");
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
        if (mApiClient != null)
            mApiClient.unregisterConnectionCallbacks(this);
        super.onDestroy();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

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

    public void onLocationChanged(Location location) {
        Log.d(TAG, "Location: " + location.getLatitude() + ", " + location.getLongitude());

        mCurrentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mBearingProvider.onLocationChanged(location);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(mCurrentLatLng, mZoom);
        mMap.animateCamera(cameraUpdate);
    }

    @Override
    public void onBearingChanged(double bearing) {

//        Log.d(TAG, "------------> " + bearing);

        // adjust bearing based on phoe orientation
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        float degree = (float) bearing + rotation * 90;

        float deg = degree;
        if (degree < 0) {
            deg += 360;
        }
        String degreeToDisplay = String.format((char) 0x2B06 + "  " + getDirectionFromDegrees(degree) + " (%.2f" + (char) 0x00B0 + ")", deg);
        mTvDirection.setText(degreeToDisplay);

        RotateAnimation ra = new RotateAnimation(
                mCurrentDegree, -degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        ra.setDuration(250);
        ra.setFillAfter(true);
        if (mIvCompass != null) {
            mIvCompass.startAnimation(ra);
        }
        mCurrentDegree = -degree;

        if (mCurrentLatLng != null) {
            CameraPosition currentPlace = new CameraPosition.Builder()
                    .target(mCurrentLatLng)
                    .bearing(degree)
//                    .tilt(65.5f)
                    .zoom(mZoom)
                    .build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(currentPlace));
        }

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

}
