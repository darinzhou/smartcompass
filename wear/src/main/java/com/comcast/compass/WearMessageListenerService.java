package com.comcast.compass;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by zzhou200 on 11/9/15.
 */
public class WearMessageListenerService extends WearableListenerService {
    public static final String MSG_START = "/msg_start";
    public static final String MSG_LATLNG = "/msg_latlng";
    public static final String MSG_ADDRESS = "/msg_address";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( messageEvent.getPath().equalsIgnoreCase( MSG_START ) ) {
            Intent intent = new Intent( this, MainActivity.class );
            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
            startActivity( intent );
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

}