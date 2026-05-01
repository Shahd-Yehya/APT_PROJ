import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * WebSocket server for collaborative editing.
 */
public class CollaborativeServer {
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, ClientSession> activeSessions;
    private final ConcurrentHashMap<String, SharedDocument> documents;

    public CollaborativeServer(int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
        this.activeSessions = new ConcurrentHashMap<>();
        this.documents = new ConcurrentHashMap<>();
    }

    /**
     * Starts the server and begins accepting client connections.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Collaborative server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connection from: " + clientSocket.getInetAddress());
                
                // Handle each client in a separate thread
                executorService.execute(new ClientHandler(clientSocket, this));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Stops the server and closes all connections.
     */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdown();
            System.out.println("Server stopped");
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }

    /**
     * Registers a new client session.
     */
    public void registerSession(String sessionId, ClientSession session) {
        activeSessions.put(sessionId, session);
        System.out.println("Session registered: " + sessionId);
    }

    /**
     * Unregisters a client session.
     */
    public void unregisterSession(String sessionId) {
        ClientSession session = activeSessions.remove(sessionId);
        if (session != null) {
            System.out.println("Session unregistered: " + sessionId);
        }
    }

    /**
     * Gets an active session.
     */
    public ClientSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * Gets or creates a shared document.
     */
    public SharedDocument getOrCreateDocument(String documentId) {
        return documents.computeIfAbsent(documentId, 
            key -> new SharedDocument(key));
    }

    /**
     * Gets all active sessions in a document.
     */
    public List<ClientSession> getSessionsInDocument(String documentId) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession session : activeSessions.values()) {
            if (documentId.equals(session.getDocumentId())) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * Broadcasts an operation to all clients in a document except the sender.
     */
    public void broadcastOperation(String documentId, String senderId, CRDTOperation operation) {
        List<ClientSession> sessions = getSessionsInDocument(documentId);
        for (ClientSession session : sessions) {
            if (!session.getSessionId().equals(senderId)) {
                session.sendRemoteOperation(operation);
            }
        }
    }

    /**
     * Main method to start the server.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8085;
        CollaborativeServer server = new CollaborativeServer(port);
        server.start();
    }
}

/**
 * ClientHandler processes client connections.
 * Handles protocol messages and operation routing.
 */
class ClientHandler implements Runnable {
    private final Socket socket;
    private final CollaborativeServer server;
    private ClientSession clientSession;
    private BufferedReader reader;
    private PrintWriter writer;

    public ClientHandler(Socket socket, CollaborativeServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Handle client protocol
            handleClient();
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleClient() throws IOException {
        String message;
        while ((message = reader.readLine()) != null) {
            // Parse and handle messages (protocol to be defined)
            // Format example: "CONNECT|sessionId|documentId|userId"
            // Format example: "OPERATION|sessionId|operationJson"
            
            if (message.startsWith("CONNECT")) {
                handleConnect(message);
            } else if (message.startsWith("OPERATION")) {
                handleOperation(message);
            } else if (message.startsWith("DISCONNECT")) {
                break;
            }
        }
    }

    private void handleConnect(String message) {
        String[] parts = message.split("\\|", 4);
        if (parts.length == 4) {
            String sessionId = parts[1];
            String documentId = parts[2];
            String userId = parts[3];

            // Create client session
            clientSession = new ClientSession(sessionId, documentId, userId, writer);
            server.registerSession(sessionId, clientSession);

            // Notify other clients
            for (ClientSession session : server.getSessionsInDocument(documentId)) {
                if (!session.getSessionId().equals(sessionId)) {
                    session.sendUserJoined(userId);
                }
            }

            writer.println("CONNECTED|" + sessionId);
        }
    }

    private void handleOperation(String message) {
        if (clientSession == null) return;

        String[] parts = message.split("\\|", 2);
        if (parts.length == 2) {
            String operationJson = parts[1];
            // Parse operation and broadcast
            // Implementation depends on JSON serialization of CRDTOperation
            CRDTOperation op = parseOperation(operationJson);
            if (op != null) {
                server.broadcastOperation(
                    clientSession.getDocumentId(),
                    clientSession.getSessionId(),
                    op
                );
            }
        }
    }

    private CRDTOperation parseOperation(String json) {
        // TODO: Implement JSON deserialization
        // This depends on how CRDTOperation is serialized
        return null;
    }

    private void cleanup() {
        try {
            if (clientSession != null) {
                server.unregisterSession(clientSession.getSessionId());
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error cleaning up: " + e.getMessage());
        }
    }
}

/**
 * Represents a client session connected to the server.
 */
class ClientSession {
    private final String sessionId;
    private final String documentId;
    private final String userId;
    private final PrintWriter writer;
    private int cursorPos;
    private CharacterId currentBlockId;

    public ClientSession(String sessionId, String documentId, String userId, PrintWriter writer) {
        this.sessionId = sessionId;
        this.documentId = documentId;
        this.userId = userId;
        this.writer = writer;
        this.cursorPos = 0;
        this.currentBlockId = BlockCRDT.ROOT_BLOCK_ID;
    }

    public String getSessionId() { return sessionId; }
    public String getDocumentId() { return documentId; }
    public String getUserId() { return userId; }
    public int getCursorPos() { return cursorPos; }
    public CharacterId getCurrentBlockId() { return currentBlockId; }

    public void setCursorPos(int pos) { this.cursorPos = pos; }
    public void setCurrentBlockId(CharacterId blockId) { this.currentBlockId = blockId; }

    public void sendRemoteOperation(CRDTOperation op) {
        // Serialize and send operation to client
        writer.println("REMOTE_OP|" + serializeOperation(op));
    }

    public void sendUserJoined(String userId) {
        writer.println("USER_JOINED|" + userId);
    }

    public void sendUserLeft(String userId) {
        writer.println("USER_LEFT|" + userId);
    }

    public void sendCursorUpdate(String userId, int cursorPos, CharacterId blockId) {
        writer.println("CURSOR_UPDATE|" + userId + "|" + cursorPos + "|" + blockId);
    }

    private String serializeOperation(CRDTOperation op) {
        // TODO: Implement JSON serialization of CRDTOperation
        return "";
    }
}

/**
 * Represents a shared document on the server.
 */
class SharedDocument {
    private final String documentId;
    private final BlockCRDT crdt;
    private final List<CRDTOperation> operationHistory;

    public SharedDocument(String documentId) {
        this.documentId = documentId;
        this.crdt = new BlockCRDT();
        this.operationHistory = Collections.synchronizedList(new ArrayList<>());
    }

    public String getDocumentId() { return documentId; }
    public BlockCRDT getCRDT() { return crdt; }
    public List<CRDTOperation> getOperationHistory() { return operationHistory; }

    public void addOperation(CRDTOperation op) {
        if (op != null) {
            operationHistory.add(op);
        }
    }
}
