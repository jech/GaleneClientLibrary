package org.galene.gclient;

import android.util.Log;

import org.webrtc.MediaConstraints;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class DownStream extends Stream implements SdpObserver {
    public DownStream(ServerConnection sc, String id, String label, Stream.Observer observer) {
        this.sc = sc;
        this.id = id;
        this.label = label;
        this.observer = observer;
    }

    public void onOffer(String sdp) {
        Log.d(TAG, "onOffer");
        pc.setRemoteDescription(this, new SessionDescription(SessionDescription.Type.OFFER, sdp));
    }

    public void onSetFailure(String e) {
        Log.d(TAG, "onSetFailure " + e);
        if(observer != null)
            observer.onError(new Error(e));
    }

    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess " + pc.signalingState());
        switch (pc.signalingState()) {
            case HAVE_REMOTE_OFFER:
                pc.createAnswer(this, new MediaConstraints());
                break;
            case STABLE:
                Message m = new Message();
                m.type = "answer";
                m.id = id;
                m.sdp = pc.getLocalDescription().description;
                sc.send(m);
                break;
            default:
                Log.wtf(TAG, "Unexpected signalling state " + pc.signalingState());
                break;
        }
    }

    public void onCreateSuccess(SessionDescription sdp) {
        Log.d(TAG, "onCreateSuccess");
        pc.setLocalDescription(this, sdp);
    }

    public void onCreateFailure(String e) {
        Log.d(TAG, "onCreateFailure" + e);
        if(observer != null)
            observer.onError(new Error(e));
    }
}
