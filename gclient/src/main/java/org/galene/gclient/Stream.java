package org.galene.gclient;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;

public abstract class Stream {
    public ServerConnection sc;
    public PeerConnection pc;
    public String id;
    public static final String TAG = "Galene-protocol";
    public String label;
    public Observer observer;

    public void close() {
        synchronized(this) {
            if (this.pc != null)
                this.pc.close();
            this.pc = null;
        }
    }

    public void onRemoteIce(JSONObject candidate) {
        String sdpMid = candidate.optString("sdpMid");
        int sdpMLineIndex = candidate.optInt("sdpMLineIndex");
        String sdp = candidate.optString("candidate");
        pc.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, sdp));
    }

    public void onLocalIce(IceCandidate candidate) {
        JSONObject c = new JSONObject();
        try {
            c.put("sdpMid", candidate.sdpMid);
            c.put("sdpMLineIndex", candidate.sdpMLineIndex);
            c.put("candidate", candidate.sdp);
        } catch(JSONException e) {
            Log.wtf(TAG, "gotLocalIce: " + e);
        }
        Message m = new Message();
        m.type = "ice";
        m.id = id;
        m.candidate = c;
        sc.send(m);
    }

    public interface Observer {
        void onError(Throwable e);
    }
}

