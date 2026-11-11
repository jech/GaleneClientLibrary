package org.galene.gclient;

public class UserMessage {
    public final String kind;
    public final String source;
    public final String dest;
    public final String username;
    public final Object value;

    public UserMessage(String kind, String source, String dest, String username, Object value) {
        this.kind = kind;
        this.source = source;
        this.dest = dest;
        this.username = username;
        this.value = value;
    }
}
