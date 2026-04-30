package controller;
import model.*;
import repository.*;
import service.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CollabServer
 * ============
 * A minimal WebSocket relay server.
 *
 * Every message it receives from one client is broadcast to ALL other
 * connected clients for the same documentId.
 *
 * This is the networking layer that Team 2 is responsible for.
 * For Phase 2 it is intentionally simple: no persistence, no auth.
 *
 * Run:  java CollabServer [port]   (default port 8080)
 */
public class CollabServer extends WebSocketServer {

    /** All currently connected sockets. */
    private final Set<WebSocket> connections =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public CollabServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    // ─── WebSocketServer callbacks ────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        System.out.println("[Server] Client connected: " + conn.getRemoteSocketAddress()
                + "  total=" + connections.size());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        System.out.println("[Server] Client disconnected: " + conn.getRemoteSocketAddress()
                + "  reason=" + reason);

        // Build a synthetic LEAVE message and broadcast so UIs update
        try {
            String attachment = (String) conn.getAttachment();
            NetworkMessage leave = NetworkMessage.fromJson(
                    attachment != null
                            ? attachment
                            : "{\"type\":\"LEAVE\",\"siteId\":-1,\"documentId\":\"\"}");
            
            relayToDocument(leave.toJson(), conn, leave.getDocumentId());
        } catch (Exception ignored) {}
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[Server] Received: " + message);

        try {
            NetworkMessage msg = NetworkMessage.fromJson(message);

            if (msg.getType() == NetworkMessage.Type.JOIN) {
                System.out.println("[Server] Site " + msg.getSiteId() + " joined document: " + msg.getDocumentId());
                
                // 1. Store the new user's JOIN info FIRST so others can see them later
                conn.setAttachment(message);

                // 2. Tell the NEW user about all existing users in the SAME document
                for (WebSocket existing : connections) {
                    if (existing != conn && existing.isOpen()) {
                        String existingInfo = (String) existing.getAttachment();
                        if (existingInfo != null) {
                            // Only send JOINs for the same document (basic filtering)
                            try {
                                NetworkMessage existingMsg = NetworkMessage.fromJson(existingInfo);
                                if (existingMsg.getDocumentId().equals(msg.getDocumentId())) {
                                    conn.send(existingInfo);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            // 3. Relay the current message to every OTHER client in the same document
            relayToDocument(message, conn, msg.getDocumentId());

        } catch (Exception e) {
            System.err.println("[Server] Could not parse message: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[Server] Error from "
                + (conn != null ? conn.getRemoteSocketAddress() : "?")
                + " : " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[Server] Listening on port " + getPort());
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    /**
     * Send to all connections EXCEPT the sender, filtered by documentId.
     */
    private void relayToDocument(String message, WebSocket sender, String documentId) {
        for (WebSocket ws : connections) {
            if (ws != sender && ws.isOpen()) {
                String attachment = (String) ws.getAttachment();
                if (attachment != null) {
                    try {
                        NetworkMessage wsMsg = NetworkMessage.fromJson(attachment);
                        if (wsMsg.getDocumentId().equals(documentId)) {
                            ws.send(message);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // ─── main ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException ignored) {}
        }
        CollabServer server = new CollabServer(port);
        server.start();
    }
}
