package org.galene.gclient;

import static org.galene.gclient.Stream.TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Message {
    public String type;
    public String[] version;
    public String kind;
    public String id;
    public String replace;
    public String source;
    public String dest;
    public String username;
    public String password;
    public String token;
    public boolean privileged;
    public String[] permissions;
    public JSONObject status;
    public JSONObject data;
    public String group;
    public Object value;
    public boolean noecho;
    public long time;
    public String sdp;
    public JSONObject candidate;
    public String label;
    public JSONObject request;
    public JSONObject rtcConfiguration;

    public Message() {
    }

    public Message(String s) throws JSONException {
        this(new JSONObject(s));
    }

    private static String get(JSONObject o, String key) {
        try {
            return o.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public Message(JSONObject o) {
        type = get(o, "type");
        JSONArray v = o.optJSONArray("version");
        if (v != null) {
            this.version = new String[v.length()];
            for (int i = 0; i < v.length(); i++)
                this.version[i] = v.optString(i);
        }
        kind = get(o, "kind");
        id = get(o, "id");
        replace = get(o, "replace");
        source = get(o, "source");
        dest = get(o, "dest");
        username = get(o, "username");
        password = get(o, "password");
        token = get(o, "token");
        privileged = o.optBoolean("privileged");
        JSONArray p = o.optJSONArray("permissions");
        if (p != null) {
            try {
                String[] pp = new String[p.length()];
                for (int i = 0; i < p.length(); i++) {
                    pp[i] = p.getString(i);
                }
                permissions = pp;
            } catch (JSONException e) {
                Log.d(TAG, "Permission value is not string");
            }
        }
        status = o.optJSONObject("status");
        data = o.optJSONObject("data");
        group = get(o, "group");
        value = o.opt("value");
        noecho = o.optBoolean("noecho");
        time = o.optLong("time");
        sdp = get(o, "sdp");
        candidate = o.optJSONObject("candidate");
        label = get(o, "label");
        request = o.optJSONObject("request");
        rtcConfiguration = o.optJSONObject("rtcConfiguration");
    }

    @NonNull
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = new JSONObject();
        o.putOpt("type", type);
        if(version != null)
            o.putOpt("version", new JSONArray(version));
        o.putOpt("kind", kind);
        o.putOpt("id", id);
        o.putOpt("replace", replace);
        o.putOpt("source", source);
        o.putOpt("dest", dest);
        o.putOpt("username", username);
        o.putOpt("password", password);
        o.putOpt("token", token);
        if(privileged)
            o.putOpt("privileged", privileged);
        if(permissions != null)
            o.putOpt("permissions", new JSONArray(permissions));
        o.putOpt("status", status);
        o.putOpt("data", data);
        o.putOpt("group", group);
        o.putOpt("value", value);
        if(noecho)
            o.putOpt("noecho", noecho);
        if(time != 0)
            o.putOpt("time", time);
        o.putOpt("sdp", sdp);
        o.putOpt("candidate", candidate);
        o.putOpt("label", label);
        o.putOpt("request", request);
        o.putOpt("rtcConfiguration", rtcConfiguration);

        return o;
    }

    @NonNull
    @Override
    public String toString() {
        try {
            return toJSONObject().toString();
        } catch(JSONException e) {
            throw new Error("Message.toString", e);
        }
    }
}
