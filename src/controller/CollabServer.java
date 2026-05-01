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
        System.out.println("[Server] CLOSE: " + conn.getRemoteSocketAddress() + " code=" + code + " reason=" + reason);

        // Build a LEAVE message and broadcast so UIs update
        try {
            String attachment = (String) conn.getAttachment();
            if (attachment != null) {
                NetworkMessage joinMsg = NetworkMessage.fromJson(attachment);
                NetworkMessage leave = new NetworkMessage(NetworkMessage.Type.LEAVE, joinMsg.getSiteId(), joinMsg.getDocumentId());
                System.out.println("[Server] Broadcasting LEAVE for site " + joinMsg.getSiteId() + " from document '" + joinMsg.getDocumentId() + "'");
                relayToDocument(leave.toJson(), conn, joinMsg.getDocumentId());
            } else {
                System.err.println("[Server] WARN: No attachment for disconnected client, cannot send LEAVE");
            }
        } catch (Exception e) {
            System.err.println("[Server] ERROR building LEAVE message: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[Server] RECV from " + conn.getRemoteSocketAddress() + ": " + message);

        try {
            NetworkMessage msg = NetworkMessage.fromJson(message);
            System.out.println("[Server] Parsed – type=" + msg.getType() + " siteId=" + msg.getSiteId() + " docId=" + msg.getDocumentId());

            if (msg.getType() == NetworkMessage.Type.JOIN) {
                System.out.println("[Server] >>> JOIN: Site " + msg.getSiteId() + " joined document '" + msg.getDocumentId() + "'");
                
                // 1. Store the new user's JOIN info FIRST so others can see them later
                conn.setAttachment(message);
                System.out.println("[Server]   Stored attachment for site " + msg.getSiteId());

                // 2. Tell the NEW user about all existing users in the SAME document
                int usersNotified = 0;
                for (WebSocket existing : connections) {
                    if (existing != conn && existing.isOpen()) {
                        String existingInfo = (String) existing.getAttachment();
                        if (existingInfo != null) {
                            try {
                                NetworkMessage existingMsg = NetworkMessage.fromJson(existingInfo);
                                if (existingMsg.getDocumentId().equals(msg.getDocumentId())) {
                                    System.out.println("[Server]   Notifying new user about site " + existingMsg.getSiteId());
                                    conn.send(existingInfo);
                                    usersNotified++;
                                }
                            } catch (Exception e) {
                                System.err.println("[Server]   WARN: Could not parse existing user: " + e.getMessage());
                            }
                        }
                    }
                }
                System.out.println("[Server]   Notified new user about " + usersNotified + " existing users");
            }

            // 3. Relay the current message to every OTHER client in the same document
            System.out.println("[Server] Relaying message to document '" + msg.getDocumentId() + "'");
            relayToDocument(message, conn, msg.getDocumentId());

        } catch (Exception e) {
            System.err.println("[Server] PARSE ERROR: " + e.getMessage());
            e.printStackTrace();
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
        int relayed = 0;
        int filtered = 0;
        int noAttachment = 0;
        
        for (WebSocket ws : connections) {
            if (ws == sender) {
                System.out.println("[Server]   Skipping sender");
                continue;
            }
            if (!ws.isOpen()) {
                System.out.println("[Server]   Skipping closed connection");
                continue;
            }
            
            String attachment = (String) ws.getAttachment();
            if (attachment == null) {
                System.err.println("[Server]   SKIPPED (no attachment/JOIN) – " + ws.getRemoteSocketAddress());
                noAttachment++;
                continue;
            }
            
            try {
                NetworkMessage wsMsg = NetworkMessage.fromJson(attachment);
                if (wsMsg.getDocumentId().equals(documentId)) {
                    System.out.println("[Server]   Relaying to site " + wsMsg.getSiteId() + " (docId match)");
                    ws.send(message);
                    relayed++;
                } else {
                    System.out.println("[Server]   Filtered out site " + wsMsg.getSiteId() + " (docId '" + wsMsg.getDocumentId() + "' != '" + documentId + "')");
                    filtered++;
                }
            } catch (Exception e) {
                System.err.println("[Server]   PARSE ERROR in attachment: " + e.getMessage());
            }
        }
        
        System.out.println("[Server] Relay summary: sent=" + relayed + " filtered=" + filtered + " noAttachment=" + noAttachment);
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
