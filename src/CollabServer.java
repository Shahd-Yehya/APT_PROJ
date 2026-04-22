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
            NetworkMessage leave = NetworkMessage.fromJson(
                    conn.getAttachment() != null
                            ? (String) conn.getAttachment()
                            : "{\"type\":\"LEAVE\",\"siteId\":-1,\"documentId\":\"\"}");
            broadcast(leave.toJson(), conn);
        } catch (Exception ignored) {}
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[Server] Received: " + message);

        try {
            NetworkMessage msg = NetworkMessage.fromJson(message);

            // On JOIN, remember who this connection belongs to
            if (msg.getType() == NetworkMessage.Type.JOIN) {
                conn.setAttachment(message); // store for LEAVE generation
            }

            // Relay to every OTHER client
            broadcast(message, conn);

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
     * Send to all connections EXCEPT the sender.
     */
    private void broadcast(String message, WebSocket sender) {
        for (WebSocket ws : connections) {
            if (ws != sender && ws.isOpen()) {
                ws.send(message);
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
