package service;
import model.*;
import repository.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket client for the collaborative editor.
 *
 * Usage:
 *   CollabClient client = new CollabClient("ws://localhost:8085", siteId, docId, handler);
 *   client.connect();
 *   client.send(message);
 */
public class CollabClient extends WebSocketClient {

    private final int      siteId;
    private final String   documentId;

    /**
     * Callback invoked on the Swing event thread whenever a NetworkMessage
     * arrives from the server.
     */
    private final Consumer<NetworkMessage> onMessage;

    /**
     * Callback invoked if connection fails or encounters an error.
     */
    private Consumer<String> onError = msg -> System.err.println("[CollabClient] " + msg);

    /**
     * Outbound queue – lets us enqueue from any thread without blocking.
     */
    private final BlockingQueue<String> outQueue = new LinkedBlockingQueue<>();

    private volatile boolean connected = false;
    
    /** Signals that connection is established and ready for messages. */
    private final CountDownLatch connectionLatch = new CountDownLatch(1);

    // ─── constructor ──────────────────────────────────────────────────────

    public CollabClient(String serverUri,
                        int siteId,
                        String documentId,
                        Consumer<NetworkMessage> onMessage) throws Exception {
        super(new URI(serverUri));
        this.siteId     = siteId;
        this.documentId = documentId;
        this.onMessage  = onMessage;
    }

    // ─── WebSocketClient callbacks ────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshake) {
        connected = true;
        System.out.println("[CollabClient] OPEN – site=" + siteId + " documentId=" + documentId);

        // Send JOIN right away so the server knows who we are
        NetworkMessage join = new NetworkMessage(
                NetworkMessage.Type.JOIN, siteId, documentId);
        String joinJson = join.toJson();
        System.out.println("[CollabClient] Sending JOIN: " + joinJson);
        sendRaw(joinJson);
        
        System.out.println("[CollabClient] Draining queued messages (queue size=" + outQueue.size() + ")");
        drainQueue();
        
        // Signal that connection is ready
        connectionLatch.countDown();
    }

    @Override
    public void onMessage(String text) {
        System.out.println("[CollabClient] RECV: " + text);
        try {
            NetworkMessage msg = NetworkMessage.fromJson(text);
            System.out.println("[CollabClient] Parsed message type=" + msg.getType() + " from site=" + msg.getSiteId());
            // Always deliver on the Swing event thread so UI code is safe
            SwingUtilities.invokeLater(() -> onMessage.accept(msg));
        } catch (Exception e) {
            System.err.println("[CollabClient] PARSE ERROR: " + e.getMessage() + " for text: " + text);
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        System.out.println("[CollabClient] CLOSE – code=" + code + " reason=" + reason + " remote=" + remote);
    }

    @Override
    public void onError(Exception ex) {
        String errMsg = "WebSocket Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        System.err.println("[CollabClient] ERROR: " + errMsg);
        ex.printStackTrace();
        if (onError != null) {
            SwingUtilities.invokeLater(() -> onError.accept(errMsg));
        }
    }

    // ─── public API ───────────────────────────────────────────────────────

    /**
     * Wait for the WebSocket connection to be established (blocking).
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if connection established, false if timeout
     */
    public boolean waitForConnection(long timeoutMs) {
        try {
            System.out.println("[CollabClient] Waiting for connection (timeout=" + timeoutMs + "ms)...");
            boolean connected = connectionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (connected) {
                System.out.println("[CollabClient] Connection established!");
            } else {
                System.err.println("[CollabClient] Connection timeout after " + timeoutMs + "ms");
            }
            return connected;
        } catch (InterruptedException e) {
            System.err.println("[CollabClient] Wait interrupted: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Set a callback for connection errors.
     */
    public void setOnError(Consumer<String> errorCallback) {
        this.onError = errorCallback;
    }

    /**
     * Send a NetworkMessage to the server.
     * Safe to call from any thread.
     */
    public void send(NetworkMessage msg) {
        sendRaw(msg.toJson());
    }

    /**
     * Convenience – build and send a CURSOR message.
     *
     * @param position      visible-char index of the local caret
     * @param blockSite     siteId component of the block the caret is in
     * @param blockClock    clock component of the block the caret is in
     */
    public void sendCursor(int position, int blockSite, int blockClock) {
        NetworkMessage m = NetworkMessage.cursor(
                siteId, documentId, position, blockSite, blockClock);
        String json = m.toJson();
        System.out.println("[CollabClient] Sending CURSOR: " + json);
        sendRaw(json);
    }

    /** Build a NetworkMessage from a CRDTOperation and send it. */
    public void sendOperation(CRDTOperation op) {
        if (op == null) return;
        NetworkMessage m = NetworkMessage.fromCRDTOperation(op, documentId);
        String json = m.toJson();
        System.out.println("[CollabClient] Sending " + m.getType() + ": " + json);
        sendRaw(json);
    }

    public boolean isConnected() {
        return connected;
    }

    // ─── private helpers ─────────────────────────────────────────────────

    private void sendRaw(String json) {
        if (isOpen()) {
            System.out.println("[CollabClient] SENDING (open): " + json);
            super.send(json);
        } else {
            // queue for retry after reconnect (basic resilience)
            System.err.println("[CollabClient] QUEUEING (not open yet): " + json);
            outQueue.offer(json);
        }
    }

    /** Drain any queued messages once we reconnect. */
    private void drainQueue() {
        String msg;
        int count = 0;
        while ((msg = outQueue.poll()) != null) {
            System.out.println("[CollabClient] DRAINING queued message #" + (++count) + ": " + msg);
            super.send(msg);
        }
        System.out.println("[CollabClient] Drained " + count + " queued messages");
    }
}
