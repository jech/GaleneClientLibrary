package org.galene.gclient;

public class ChatMessage {
    public final String kind;
    public final String source;
    public final String dest;
    public final String username;
    public final String value;

    public ChatMessage(String kind, String source, String dest, String username, String value) {
        this.kind = kind;
        this.source = source;
        this.dest = dest;
        this.username = username;
        this.value = value;

    }
}
