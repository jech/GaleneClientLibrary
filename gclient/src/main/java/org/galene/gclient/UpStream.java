package org.galene.gclient;

import android.util.Log;

import org.webrtc.MediaConstraints;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class UpStream extends Stream implements SdpObserver {
    private boolean negotiationNeeded = false;

    public UpStream(ServerConnection sc, String id, String label, Stream.Observer observer) {
        this.sc = sc;
        this.id = id;
        this.label = label;
        this.observer = observer;
    }

    public void negotiate() {
        switch(pc.signalingState()) {
            case STABLE:
                pc.createOffer(this, new MediaConstraints());
                break;
            default:
                negotiationNeeded = true;
        }
    }

    public void onSetFailure(String e) {
        Log.d(TAG, "onSetFailure " + e);
        if (observer != null)
            observer.onError(new Error(e));
    }

    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess " + pc.signalingState());
        switch(pc.signalingState()) {
            case HAVE_LOCAL_OFFER:
                SessionDescription sdp = pc.getLocalDescription();
                Message m = new Message();
                m.type = "offer";
                m.id = id;
                m.label = label;
                m.sdp = sdp.description;
                sc.send(m);
                break;
            case STABLE:
                if(negotiationNeeded) {
                    negotiationNeeded = false;
                    negotiate();
                }
                break;
            default:
                Log.wtf(TAG, "Unexpected signalling state " + pc.signalingState());
                break;
        }
    }

    public void onAnswer(String sdp) {
        SessionDescription desc = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        pc.setRemoteDescription(this, desc);
    }

    public void onCreateFailure(String e) {
        Log.d(TAG, "onCreateFailure: " + e);
        if (observer != null)
            observer.onError(new Error(e));
    }

    public void onCreateSuccess(SessionDescription sdp) {
        Log.d(TAG, "onCreateSuccess " + sdp.type);
        pc.setLocalDescription(this);
    }
}
