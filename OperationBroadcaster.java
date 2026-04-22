import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts operations to connected clients.
 */
public class OperationBroadcaster {
    private final CollaborativeServer server;
    private final ConcurrentHashMap<String, OperationLog> documentLogs;

    public OperationBroadcaster(CollaborativeServer server) {
        this.server = server;
        this.documentLogs = new ConcurrentHashMap<>();
    }

    /**
     * Broadcasts a local operation to all remote clients in a document.
     * 
     * @param documentId ID of the document
     * @param senderId   Session ID of the sender
     * @param operation  The CRDT operation to broadcast
     */
    public void broadcastLocalOperation(String documentId, String senderId, CRDTOperation operation) {
        // Serialize operation to JSON
        String operationJson = serializeOperation(operation);

        // Log operation
        OperationLog log = documentLogs.computeIfAbsent(documentId, 
            key -> new OperationLog(documentId));
        log.addOperation(senderId, operation, operationJson);

        // Get all other clients in the document and send them the operation
        List<ClientSession> sessions = server.getSessionsInDocument(documentId);
        for (ClientSession session : sessions) {
            if (!session.getSessionId().equals(senderId)) {
                sendOperationToClient(session, operation);
            }
        }

        System.out.println("Broadcasted operation in document " + documentId + 
            " from client " + senderId);
    }

    /**
     * Sends an operation to a specific client.
     */
    public void sendOperationToClient(ClientSession client, CRDTOperation operation) {
        String operationJson = serializeOperation(operation);
        client.sendRemoteOperation(operation);
    }

    /**
     * Serializes a CRDTOperation to string format.
     * Format: TYPE|siteId|charId|blockId|value|bold|italic|...
     */
    public String serializeOperation(CRDTOperation operation) {
        OperationDTO dto = new OperationDTO(operation);
        return dto.toString();
    }

    /**
     * Deserializes string to CRDTOperation.
     */
    public CRDTOperation deserializeOperation(String str) {
        try {
            OperationDTO dto = OperationDTO.fromString(str);
            return dto.toCRDTOperation();
        } catch (Exception e) {
            System.err.println("Error deserializing operation: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the operation log for a document.
     */
    public OperationLog getOperationLog(String documentId) {
        return documentLogs.get(documentId);
    }

    /**
     * Gets all operations for a document in order.
     */
    public List<CRDTOperation> getDocumentOperations(String documentId) {
        OperationLog log = documentLogs.get(documentId);
        if (log != null) {
            return log.getAllOperations();
        }
        return new ArrayList<>();
    }

    /**
     * Replays all operations from a specific point for a new client.
     * Used when a client joins to sync existing state.
     */
    public void replayOperationsToClient(ClientSession client, String documentId) {
        OperationLog log = documentLogs.get(documentId);
        if (log == null) return;

        List<CRDTOperation> operations = log.getAllOperations();
        for (CRDTOperation op : operations) {
            sendOperationToClient(client, op);
        }

        System.out.println("Replayed " + operations.size() + 
            " operations to client " + client.getSessionId());
    }
}

/**
 * DTO (Data Transfer Object) for serializing/deserializing CRDTOperation.
 * Uses pipe-delimited format instead of JSON to avoid external dependencies.
 */
class OperationDTO {
    private String type;
    private CharacterIdDTO charId;
    private CharacterIdDTO parentId;
    private char value;
    private CharacterIdDTO blockId;
    private boolean bold;
    private boolean italic;
    private boolean prevBold;
    private boolean prevItalic;
    private CharacterIdDTO blockParentId;
    private int siteId;

    public OperationDTO() {}

    public OperationDTO(CRDTOperation op) {
        this.type = op.getType().toString();
        this.charId = op.getCharId() != null ? new CharacterIdDTO(op.getCharId()) : null;
        this.parentId = op.getParentId() != null ? new CharacterIdDTO(op.getParentId()) : null;
        this.value = op.getValue();
        this.blockId = op.getBlockId() != null ? new CharacterIdDTO(op.getBlockId()) : null;
        this.bold = op.isBold();
        this.italic = op.isItalic();
        this.prevBold = op.isPrevBold();
        this.prevItalic = op.isPrevItalic();
        this.blockParentId = op.getBlockParentId() != null ? 
            new CharacterIdDTO(op.getBlockParentId()) : null;
        this.siteId = op.getSiteId();
    }

    @Override
    public String toString() {
        // Serialize to pipe-delimited format
        return type + "|" + siteId + "|" + 
            (charId != null ? charId.toString() : "null") + "|" +
            (parentId != null ? parentId.toString() : "null") + "|" +
            value + "|" +
            (blockId != null ? blockId.toString() : "null") + "|" +
            bold + "|" + italic + "|" +
            prevBold + "|" + prevItalic + "|" +
            (blockParentId != null ? blockParentId.toString() : "null");
    }

    static OperationDTO fromString(String str) {
        String[] parts = str.split("\\|");
        if (parts.length < 11) {
            throw new IllegalArgumentException("Invalid operation format");
        }

        OperationDTO dto = new OperationDTO();
        dto.type = parts[0];
        dto.siteId = Integer.parseInt(parts[1]);
        dto.charId = !parts[2].equals("null") ? CharacterIdDTO.fromString(parts[2]) : null;
        dto.parentId = !parts[3].equals("null") ? CharacterIdDTO.fromString(parts[3]) : null;
        dto.value = parts[4].isEmpty() ? '\0' : parts[4].charAt(0);
        dto.blockId = !parts[5].equals("null") ? CharacterIdDTO.fromString(parts[5]) : null;
        dto.bold = Boolean.parseBoolean(parts[6]);
        dto.italic = Boolean.parseBoolean(parts[7]);
        dto.prevBold = Boolean.parseBoolean(parts[8]);
        dto.prevItalic = Boolean.parseBoolean(parts[9]);
        dto.blockParentId = !parts[10].equals("null") ? CharacterIdDTO.fromString(parts[10]) : null;

        return dto;
    }

    public CRDTOperation toCRDTOperation() {
        CharacterId charIdObj = charId != null ? charId.toCharacterId() : null;
        CharacterId parentIdObj = parentId != null ? parentId.toCharacterId() : null;
        CharacterId blockIdObj = blockId != null ? blockId.toCharacterId() : null;
        CharacterId blockParentIdObj = blockParentId != null ? 
            blockParentId.toCharacterId() : null;

        CRDTOperation.Type opType = CRDTOperation.Type.valueOf(type);

        switch (opType) {
            case INSERT_CHAR:
                return CRDTOperation.insertChar(siteId, charIdObj, value, parentIdObj, blockIdObj);
            case DELETE_CHAR:
                return CRDTOperation.deleteChar(siteId, charIdObj, blockIdObj);
            case UNDELETE_CHAR:
                return CRDTOperation.undeleteChar(siteId, charIdObj, blockIdObj);
            case FORMAT_CHAR:
                return CRDTOperation.formatChar(siteId, charIdObj, blockIdObj, 
                    bold, italic, prevBold, prevItalic);
            case INSERT_BLOCK:
                return CRDTOperation.insertBlock(siteId, blockIdObj, blockParentIdObj);
            case DELETE_BLOCK:
                return CRDTOperation.deleteBlock(siteId, blockIdObj);
            default:
                throw new IllegalArgumentException("Unknown operation type: " + type);
        }
    }

    // Getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public CharacterIdDTO getCharId() { return charId; }
    public void setCharId(CharacterIdDTO charId) { this.charId = charId; }
    public CharacterIdDTO getParentId() { return parentId; }
    public void setParentId(CharacterIdDTO parentId) { this.parentId = parentId; }
    public char getValue() { return value; }
    public void setValue(char value) { this.value = value; }
    public CharacterIdDTO getBlockId() { return blockId; }
    public void setBlockId(CharacterIdDTO blockId) { this.blockId = blockId; }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
    public boolean isPrevBold() { return prevBold; }
    public void setPrevBold(boolean prevBold) { this.prevBold = prevBold; }
    public boolean isPrevItalic() { return prevItalic; }
    public void setPrevItalic(boolean prevItalic) { this.prevItalic = prevItalic; }
    public CharacterIdDTO getBlockParentId() { return blockParentId; }
    public void setBlockParentId(CharacterIdDTO blockParentId) { this.blockParentId = blockParentId; }
    public int getSiteId() { return siteId; }
    public void setSiteId(int siteId) { this.siteId = siteId; }
}

/**
 * DTO for CharacterId serialization.
 */
class CharacterIdDTO {
    private int siteId;
    private int clock;

    public CharacterIdDTO() {}

    public CharacterIdDTO(CharacterId id) {
        this.siteId = id.getSiteId();
        this.clock = id.getClock();
    }

    @Override
    public String toString() {
        return siteId + "," + clock;
    }

    static CharacterIdDTO fromString(String str) {
        String[] parts = str.split(",");
        CharacterIdDTO dto = new CharacterIdDTO();
        dto.siteId = Integer.parseInt(parts[0]);
        dto.clock = Integer.parseInt(parts[1]);
        return dto;
    }

    public CharacterId toCharacterId() {
        return new CharacterId(siteId, clock);
    }

    public int getSiteId() { return siteId; }
    public void setSiteId(int siteId) { this.siteId = siteId; }
    public int getClock() { return clock; }
    public void setClock(int clock) { this.clock = clock; }
}

/**
 * Logs all operations for a document for auditing and replay purposes.
 */
class OperationLog {
    private final String documentId;
    private final List<OperationEntry> entries;

    public OperationLog(String documentId) {
        this.documentId = documentId;
        this.entries = Collections.synchronizedList(new ArrayList<>());
    }

    public void addOperation(String senderId, CRDTOperation operation, String json) {
        entries.add(new OperationEntry(senderId, operation, json, System.currentTimeMillis()));
    }

    public List<CRDTOperation> getAllOperations() {
        List<CRDTOperation> result = new ArrayList<>();
        for (OperationEntry entry : entries) {
            result.add(entry.operation);
        }
        return result;
    }

    public List<OperationEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public int size() {
        return entries.size();
    }

    static class OperationEntry {
        String senderId;
        CRDTOperation operation;
        String json;
        long timestamp;

        OperationEntry(String senderId, CRDTOperation operation, String json, long timestamp) {
            this.senderId = senderId;
            this.operation = operation;
            this.json = json;
            this.timestamp = timestamp;
        }
    }
}
