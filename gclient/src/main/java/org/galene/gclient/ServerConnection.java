package org.galene.gclient;

import static org.webrtc.PeerConnection.IceConnectionState.FAILED;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.BuiltinAudioDecoderFactoryFactory;
import org.webrtc.BuiltinAudioEncoderFactoryFactory;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerConnection {
    private final WebSocket ws;
    public Map<String, User> users;
    public String group;
    public String username;
    private PeerConnection.RTCConfiguration rtcConfiguration;
    private final Observer observer;
    private final String id;
    private boolean handshakeDone = false;
    private static final String TAG = "Galene-protocol";
    public PeerConnectionFactory factory;
    public Map<String, UpStream> up;
    public Map<String, DownStream> down;

    public static void initialize(Context context) {
        Log.d(TAG, "Initialise");
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).
                        createInitializationOptions());
    }

    @WorkerThread
    public ServerConnection(Context context, String endpoint, Observer adapter) throws IOException {
        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();

        AudioDeviceModule adm = JavaAudioDeviceModule.builder(context).createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setAudioDecoderFactoryFactory(new BuiltinAudioDecoderFactoryFactory())
                .setAudioEncoderFactoryFactory(new BuiltinAudioEncoderFactoryFactory())
                .setVideoEncoderFactory(new SoftwareVideoEncoderFactory())
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
                .createPeerConnectionFactory();

        id = makeId();
        up = new HashMap<>();
        down = new HashMap<>();
        users = new HashMap<>();
        WebSocketFactory wsFactory = new WebSocketFactory().setConnectionTimeout(20000);
        ws = wsFactory.createSocket(endpoint);
        ws.addListener(new WSAdapter(this));
        this.observer = adapter;
    }

    @NonNull
    private static String makeId() {
        byte[] a = new byte[16];
        new SecureRandom().nextBytes(a);
        byte[] b = Base64.encode(a, Base64.NO_PADDING | Base64.NO_WRAP);
        return new String(b);
    }

    @WorkerThread
    public void connect() throws WebSocketException {
        ws.connect();
        Message m = new Message();
        m.type = "handshake";
        m.version = new String[2];
        m.version[0] = "2";
        m.version[1] = "1";
        m.id = id;
        send(m);
    }

    @AnyThread
    public void send(Message m) {
        String s = m.toString();
        Log.d(TAG, "-> " + s);
        ws.sendText(s);
    }

    @AnyThread
    public void join(String group, String username, String password, String token) {
        if (this.group != null)
            throw new Error("Cannot join multiple times");
        this.username = username;
        Message m = new Message();
        m.type = "join";
        m.kind = "join";
        m.group = group;
        if(token == null || (username != null && !username.equals("")))
            m.username = username;
        if(token != null && !token.equals(""))
            // don't send password if we have a token
            m.token = token;
        else
            m.password = password;
        send(m);
    }

    @AnyThread
    public void request(boolean receive) {
        JSONObject what = new JSONObject();
        if (receive) {
            String[] a = new String[1];
            a[0] = "audio";
            try {
                what.put("", new JSONArray(a));
            } catch (JSONException e) {
                Log.wtf(TAG, "build array", e);
            }
        }
        Message m = new Message();
        m.type = "request";
        m.request = what;
        send(m);
    }

    @AnyThread
    public void chat(String kind, String dest, String s) {
        Message m = new Message();
        m.type = "chat";
        m.kind = kind;
        m.dest = dest;
        m.username = username;
        m.value = s;
        send(m);
    }

    @AnyThread
    public void close() {
        ws.disconnect();
    }

    ArrayList<PeerConnection.IceServer> getIceServers(JSONArray ss) throws JSONException {
        ArrayList<PeerConnection.IceServer> servers = new ArrayList<>();
        for (int i = 0; i < ss.length(); i++) {
            JSONObject s = ss.getJSONObject(i);
            JSONArray jurls;
            jurls = s.getJSONArray("urls");
            ArrayList<String> urls = new ArrayList<>();
            for (int j = 0; j < jurls.length(); j++) {
                String url = jurls.getString(i);
                urls.add(url);
            }
            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);
            String username = s.optString("username");
            if(!username.equals(""))
                builder.setUsername(username);
            String password = s.optString("credential");
            if(!password.equals(""))
                builder.setPassword(password);
            servers.add(builder.createIceServer());
        }
        return servers;

    }

    PeerConnection.RTCConfiguration getRTCConfiguration(JSONObject conf) throws JSONException {
        if (conf == null)
            return new PeerConnection.RTCConfiguration(new ArrayList<>());
        JSONArray ss = conf.getJSONArray("iceServers");
        return new PeerConnection.RTCConfiguration(getIceServers(ss));
    }

    public void onMessage(Message m) {
        Log.d(TAG, "<- " + m);
        if (m.type == null) {
            onError(new Error("No type in message"));
            return;
        }

        if (m.type.equals("handshake")) {
            boolean versionError = true;
            if (m.version != null) {
                for (int i = 0; i < m.version.length; i++) {
                    if (m.version[i].equals("2") || m.version[i].equals("1")) {
                        versionError = false;
                        break;
                    }
                }
            }
            if (versionError) {
                onError(new Error("Unexpected protocol version"));
                return;
            }
            handshakeDone = true;
            observer.onConnected(true);
            return;
        }

        if (!handshakeDone) {
            onError(new Error("Server didn't handshake"));
            return;
        }

        if (m.type.equals("ping")) {
            Message n = new Message();
            n.type = "pong";
            send(n);
        } else if (m.type.equals("joined")) {
            if (m.kind.equals("join") || m.kind.equals("change")) {
                this.group = m.group;
                this.username = m.username;
                if (m.kind.equals("join"))
                    this.users.clear();
                try {
                    rtcConfiguration = getRTCConfiguration(m.rtcConfiguration);
                } catch (JSONException e) {
                    Log.w(TAG, "Couldn't get RTC configuration", e);
                    rtcConfiguration = new PeerConnection.RTCConfiguration(
                            new ArrayList<>());
                }
            } else {
                this.group = null;
                this.username = null;
                this.users.clear();
                rtcConfiguration = null;
            }

            String v = null;
            if (m.value != null)
                v = m.value.toString();
            observer.onJoined(m.kind, m.group, v);
        } else if (m.type.equals("offer")) {
            UpStream s = up.get(m.id);
            if (s != null) {
                Log.w(TAG, "Got offer for my own stream");
                sendAbort(m.id);
                return;
            }
            if (m.replace != null)
                closeDownStream(m.replace);
            DownStream stream = down.get(m.id);
            if (stream == null) {
                stream = makeDownStream(m.id, m.label);
            }
            stream.onOffer(m.sdp);
        } else if (m.type.equals("answer")) {
            UpStream stream = up.get(m.id);
            if (stream == null) {
                Log.w(TAG, "answer for unknown stream");
                return;
            }
            stream.onAnswer(m.sdp);
        } else if (m.type.equals("ice")) {
            Stream stream = up.get(m.id);
            if (stream == null) {
                stream = down.get(m.id);
            }
            if (stream == null) {
                Log.w(TAG, "ice for unknown stream");
                return;
            }
            stream.onRemoteIce(m.candidate);
        } else if (m.type.equals("close")) {
            closeDownStream(m.id);
        } else if (m.type.equals("abort")) {
            closeUpStream(m.id);
        } else if (m.type.equals("renegotiate")) {
            Stream stream = up.get(m.id);
            if (stream == null) {
                Log.w(TAG, "renegotiate for unknown stream");
                return;
            }
            stream.pc.restartIce();
        } else if (m.type.equals("chat") || m.type.equals("chathistory")) {
            String value;
            try {
                value = (String) m.value;
            } catch (ClassCastException e) {
                Log.e(TAG, "Chat value is " + m.value);
                return;
            }
            observer.onChat(new ChatMessage(m.kind, m.source, m.dest, m.username, value));
        } else if (m.type.equals("user")) {
            User u;
            if (m.kind.equals("add")) {
                u = new User(m.id, m.username);
                users.put(m.id, u);
            } else if (m.kind.equals("change")) {
                User v = users.get(m.id);
                if (v == null) {
                    Log.w(TAG, "Got change for unknown user " + id);
                    return;
                }
                u = new User(m.id, m.username);
                users.put(m.id, u);
            } else if (m.kind.equals("delete")) {
                u = users.get(m.id);
                if (u == null) {
                    Log.w(TAG, "Got delete for unknown user " + id);
                    return;
                }
                users.remove(m.id);
            } else {
                Log.w(TAG, "Unexpected user message " + m.kind);
                return;
            }
            observer.onUser(m.kind, u);
        } else if (m.type.equals("usermessage")) {
            observer.onUsermessage(new UserMessage(m.kind, m.id, m.dest, m.username, m.value));
        } else {
            Log.w(TAG, "Unknown message type " + m.type);
        }
    }

    protected void onError(Throwable e) {
        Log.w(TAG, "Server connection", e);
        observer.onError(e);
    }

    protected void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        Stream[] values = new Stream[0];
        synchronized (this) {
            values = up.values().toArray(values);
            up.clear();
        }
        for (Stream s : values)
            s.close();
        observer.onConnected(false);
    }

    @AnyThread
    @NonNull
    public Stream makeUpStream(String label, MediaStreamTrack track) {
        final String id = makeId();
        final UpStream s = new UpStream(this, id, label, new Stream.Observer() {
            public void onError(Throwable e) {
                UpStream ss;
                synchronized (this) {
                    ss = up.get(id);
                    up.remove(id);
                }
                if (ss == null) {
                    Log.w(TAG, "OnError for unknown stream", e);
                    return;
                }

                Message m = new Message();
                m.type = "close";
                m.id = id;
                send(m);
            }
        });

        PeerConnection pc = factory.createPeerConnection(rtcConfiguration, new PeerConnection.Observer() {
            public void onAddStream(MediaStream stream) {
            }

            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            }

            public void onDataChannel(DataChannel dataChannel) {
            }

            public void onIceCandidate(IceCandidate candidate) {
                s.onLocalIce(candidate);
            }

            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                Log.d(TAG, "ICE state " + newState);
                if (newState == FAILED) {
                    PeerConnection pc;
                    synchronized (s) {
                        pc = s.pc;
                    }
                    if (pc != null)
                        pc.restartIce();
                }
            }

            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "onIceConnectionReceivingChange " + receiving);
            }

            public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
                Log.d(TAG, "ICE Gathering state " + newState);
            }

            public void onRemoveStream(MediaStream stream) {
            }

            public void onRenegotiationNeeded() {
                s.negotiate();
            }

            public void onSignalingChange(PeerConnection.SignalingState newState) {
                Log.d(TAG, "Signaling state " + newState);
            }
        });
        if(pc == null)
            throw new Error("Couldn't create peer connection");
        s.pc = pc;
        synchronized (this) {
            up.put(id, s);
        }
        pc.addTrack(track, Collections.singletonList(label));
        return s;
    }

    @AnyThread
    @NonNull
    public DownStream makeDownStream(String id, String label) {
        final DownStream s = new DownStream(this, id, label, new Stream.Observer() {
            public void onError(Throwable e) {
                DownStream ss;
                synchronized (this) {
                    ss = down.get(id);
                    down.remove(id);
                }
                if (ss == null) {
                    Log.w(TAG, "OnError for unknown stream", e);
                    return;
                }
                Message m = new Message();
                m.type = "abort";
                m.id = id;
                send(m);
            }
        });

        PeerConnection pc = factory.createPeerConnection(rtcConfiguration, new PeerConnection.Observer() {
            public void onAddStream(MediaStream stream) {
                Log.d(TAG, "Got stream " + stream);
            }

            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                MediaStreamTrack track = receiver.track();
                Log.d(TAG, "Got track " + (track != null ? track.kind() : "(null)"));
            }

            public void onDataChannel(DataChannel dataChannel) {
            }

            public void onIceCandidate(IceCandidate candidate) {
                s.onLocalIce(candidate);
            }

            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                Log.d(TAG, "ICE state " + newState);
                if (newState == FAILED) {
                    Message m = new Message();
                    m.type = "renegotiate";
                    m.id = id;
                    send(m);
                }
            }

            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "onIceConnectionReceivingChange " + receiving);
            }

            public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
                Log.d(TAG, "ICE Gathering state " + newState);
            }

            public void onRemoveStream(MediaStream stream) {
            }

            public void onRenegotiationNeeded() {
            }

            public void onSignalingChange(PeerConnection.SignalingState newState) {
                Log.d(TAG, "Signaling state " + newState);
            }
        });
        if(pc == null)
            throw new Error("Couldn't create peer connection");
        s.pc = pc;
        synchronized (this) {
            down.put(id, s);
        }
        return s;
    }

    @AnyThread
    public void closeUpStream(String id) {
        Stream s;
        synchronized (this) {
            s = up.get(id);
            up.remove(id);
        }
        if (s == null) {
            Log.w(TAG, "Attempting to close unknown up stream");
            return;
        }
        Message m = new Message();
        m.type = "close";
        m.id = s.id;
        send(m);
        s.close();
    }

    @AnyThread
    public void sendAbort(String id) {
        Message m = new Message();
        m.type = "abort";
        m.id = id;
        send(m);
    }

    @AnyThread
    public void closeDownStream(String id) {
        Stream s;
        synchronized (this) {
            s = down.get(id);
            down.remove(id);
        }
        if (s == null) {
            Log.w(TAG, "Attempting to close unknown down stream");
            return;
        }
        s.close();
    }

    public synchronized UpStream getUpStreamByLabel(String label) {
        for (UpStream s : up.values()) {
            if (s.label.equals(label))
                return s;
        }
        return null;
    }

    public static class State {
        public String group;
        public String username;
        public ArrayList<User> users;
    }

    public synchronized State getState() {
        State s = new State();
        s.username = username;
        s.group = group;
        s.users = new ArrayList<>(this.users.values());
        return s;
    }

    public interface Observer {
        void onConnected(boolean connected);

        void onJoined(String kind, String group, String message);

        void onChat(ChatMessage chat);

        void onError(Throwable e);

        void onUser(String kind, User user);

        void onUsermessage(UserMessage message);
    }

    static class WSAdapter extends WebSocketAdapter {
        private final ServerConnection conn;

        public WSAdapter(ServerConnection conn) {
            this.conn = conn;
        }

        @Override
        public void onTextMessage(WebSocket ws, String s) {
            try {
                Message m = new Message(s);
                conn.onMessage(m);
            } catch (Exception e) {
                conn.onError(e);
            }
        }

        @Override
        public void onDisconnected(WebSocket ws, WebSocketFrame sf, WebSocketFrame cf, boolean server) {
            conn.onDisconnected();
        }
    }
}
