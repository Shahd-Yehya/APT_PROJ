import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * WebSocket client for the collaborative editor.
 *
 * Usage:
 *   CollabClient client = new CollabClient("ws://localhost:8080", siteId, docId, handler);
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
     * Outbound queue – lets us enqueue from any thread without blocking.
     */
    private final BlockingQueue<String> outQueue = new LinkedBlockingQueue<>();

    private volatile boolean connected = false;

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
        System.out.println("[CollabClient] Connected – site=" + siteId);

        // Send JOIN right away so the server knows who we are
        NetworkMessage join = new NetworkMessage(
                NetworkMessage.Type.JOIN, siteId, documentId);
        sendRaw(join.toJson());
    }

    @Override
    public void onMessage(String text) {
        try {
            NetworkMessage msg = NetworkMessage.fromJson(text);
            // Always deliver on the Swing event thread so UI code is safe
            SwingUtilities.invokeLater(() -> onMessage.accept(msg));
        } catch (Exception e) {
            System.err.println("[CollabClient] Bad message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        System.out.println("[CollabClient] Disconnected – " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[CollabClient] Error: " + ex.getMessage());
    }

    // ─── public API ───────────────────────────────────────────────────────

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
        sendRaw(m.toJson());
    }

    /** Build a NetworkMessage from a CRDTOperation and send it. */
    public void sendOperation(CRDTOperation op) {
        if (op == null) return;
        NetworkMessage m = NetworkMessage.fromCRDTOperation(op, documentId);
        sendRaw(m.toJson());
    }

    public boolean isConnected() {
        return connected;
    }

    // ─── private helpers ─────────────────────────────────────────────────

    private void sendRaw(String json) {
        if (isOpen()) {
            super.send(json);
        } else {
            // queue for retry after reconnect (basic resilience)
            outQueue.offer(json);
            System.err.println("[CollabClient] Not open – queued: " + json);
        }
    }

    /** Drain any queued messages once we reconnect. */
    private void drainQueue() {
        String msg;
        while ((msg = outQueue.poll()) != null) {
            super.send(msg);
        }
    }
}
