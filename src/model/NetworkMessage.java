package model;
import repository.*;
import org.json.JSONObject;
/**
 * Represents every message exchanged between client and server.
 *
 * Message types:
 *   INSERT_CHAR   – a character was inserted
 *   DELETE_CHAR   – a character was deleted
 *   FORMAT_CHAR   – a character's formatting changed
 *   INSERT_BLOCK  – a new block was inserted
 *   DELETE_BLOCK  – a block was deleted
 *   CURSOR        – a user's cursor moved
 *   JOIN          – a user joined the session
 *   LEAVE         – a user left the session
 *   INIT          – server sends full doc state on join
 *   ACK           – server acknowledges a message
 *   ERROR         – server signals an error
 */
public class NetworkMessage {
    public enum Type {
        INSERT_CHAR, DELETE_CHAR, FORMAT_CHAR,
        INSERT_BLOCK, DELETE_BLOCK,
        CURSOR,
        JOIN, LEAVE, INIT, ACK, ERROR
    }
    // ─── core fields (always present) ────────────────────────────────────
    private final Type   type;
    private final int    siteId;       // who originated the message
    private final String documentId;  // which document
    // ─── char-op fields ──────────────────────────────────────────────────
    private int    charSite;
    private int    charClock;
    private char   charValue;
    private int    parentSite;
    private int    parentClock;
    private int    blockSite;
    private int    blockClock;
    private boolean bold;
    private boolean italic;
    // ─── block-op fields ─────────────────────────────────────────────────
    private int blockParentSite;
    private int blockParentClock;
    // ─── cursor fields ───────────────────────────────────────────────────
    /** character-level position (index in visible chars) of the cursor */
    private int cursorPosition;
    /** which block the cursor is in */
    private int cursorBlockSite;
    private int cursorBlockClock;
    // ─── misc ─────────────────────────────────────────────────────────────
    private String payload;   // INIT: full doc JSON / ERROR: message text
    // ─── constructors ─────────────────────────────────────────────────────
    public NetworkMessage(Type type, int siteId, String documentId) {
        this.type       = type;
        this.siteId     = siteId;
        this.documentId = documentId;
    }
    // ─── factory helpers ──────────────────────────────────────────────────
    /** Build a CURSOR message. */
    public static NetworkMessage cursor(int siteId, String docId,
                                        int position,
                                        int blockSite, int blockClock) {
        NetworkMessage m = new NetworkMessage(Type.CURSOR, siteId, docId);
        m.cursorPosition   = position;
        m.cursorBlockSite  = blockSite;
        m.cursorBlockClock = blockClock;
        return m;
    }
    /** Build from a CRDTOperation (char or block). */
    public static NetworkMessage fromCRDTOperation(CRDTOperation op,
                                                   String docId) {
        Type t;
        switch (op.getType()) {
            case INSERT_CHAR:  t = Type.INSERT_CHAR;  break;
            case DELETE_CHAR:  t = Type.DELETE_CHAR;  break;
            case FORMAT_CHAR:  t = Type.FORMAT_CHAR;  break;
            case INSERT_BLOCK: t = Type.INSERT_BLOCK; break;
            case DELETE_BLOCK: t = Type.DELETE_BLOCK; break;
            default: throw new IllegalArgumentException(
                    "Unsupported op type: " + op.getType());
        }
        NetworkMessage m = new NetworkMessage(t, op.getSiteId(), docId);
        if (op.getCharId() != null) {
            m.charSite  = op.getCharId().getSiteId();
            m.charClock = op.getCharId().getClock();
        }
        if (op.getParentId() != null) {
            m.parentSite  = op.getParentId().getSiteId();
            m.parentClock = op.getParentId().getClock();
        }
        if (op.getBlockId() != null) {
            m.blockSite  = op.getBlockId().getSiteId();
            m.blockClock = op.getBlockId().getClock();
        }
        if (op.getBlockParentId() != null) {
            m.blockParentSite  = op.getBlockParentId().getSiteId();
            m.blockParentClock = op.getBlockParentId().getClock();
        }
        m.charValue = op.getValue();
        m.bold      = op.isBold();
        m.italic    = op.isItalic();
        return m;
    }
    // ─── serialisation ────────────────────────────────────────────────────
    public String toJson() {
        JSONObject o = new JSONObject();
        o.put("type",       type.name());
        o.put("siteId",     siteId);
        o.put("documentId", documentId);
        switch (type) {
            case INSERT_CHAR:
                o.put("charSite",    charSite);
                o.put("charClock",   charClock);
                o.put("charValue",   String.valueOf(charValue));
                o.put("parentSite",  parentSite);
                o.put("parentClock", parentClock);
                o.put("blockSite",   blockSite);
                o.put("blockClock",  blockClock);
                o.put("bold",        bold);
                o.put("italic",      italic);
                break;
            case DELETE_CHAR:
            case FORMAT_CHAR:
                o.put("charSite",   charSite);
                o.put("charClock",  charClock);
                o.put("blockSite",  blockSite);
                o.put("blockClock", blockClock);
                if (type == Type.FORMAT_CHAR) {
                    o.put("bold",   bold);
                    o.put("italic", italic);
                }
                break;
            case INSERT_BLOCK:
                o.put("blockSite",        blockSite);
                o.put("blockClock",       blockClock);
                o.put("blockParentSite",  blockParentSite);
                o.put("blockParentClock", blockParentClock);
                break;
            case DELETE_BLOCK:
                o.put("blockSite",  blockSite);
                o.put("blockClock", blockClock);
                break;
            case CURSOR:
                o.put("cursorPosition",   cursorPosition);
                o.put("cursorBlockSite",  cursorBlockSite);
                o.put("cursorBlockClock", cursorBlockClock);
                break;
            case INIT:
            case ERROR:
                if (payload != null) o.put("payload", payload);
                break;
            default:
                break;
        }
        return o.toString();
    }
    public static NetworkMessage fromJson(String json) {
        JSONObject o    = new JSONObject(json);
        Type   type     = Type.valueOf(o.getString("type"));
        int    siteId   = o.getInt("siteId");
        String docId    = o.optString("documentId", "");
        NetworkMessage m = new NetworkMessage(type, siteId, docId);
        switch (type) {
            case INSERT_CHAR:
                m.charSite    = o.getInt("charSite");
                m.charClock   = o.getInt("charClock");
                m.charValue   = o.getString("charValue").charAt(0);
                m.parentSite  = o.getInt("parentSite");
                m.parentClock = o.getInt("parentClock");
                m.blockSite   = o.getInt("blockSite");
                m.blockClock  = o.getInt("blockClock");
                m.bold        = o.optBoolean("bold",   false);
                m.italic      = o.optBoolean("italic", false);
                break;
            case DELETE_CHAR:
            case FORMAT_CHAR:
                m.charSite   = o.getInt("charSite");
                m.charClock  = o.getInt("charClock");
                m.blockSite  = o.getInt("blockSite");
                m.blockClock = o.getInt("blockClock");
                if (type == Type.FORMAT_CHAR) {
                    m.bold   = o.optBoolean("bold",   false);
                    m.italic = o.optBoolean("italic", false);
                }
                break;
            case INSERT_BLOCK:
                m.blockSite        = o.getInt("blockSite");
                m.blockClock       = o.getInt("blockClock");
                m.blockParentSite  = o.getInt("blockParentSite");
                m.blockParentClock = o.getInt("blockParentClock");
                break;
            case DELETE_BLOCK:
                m.blockSite  = o.getInt("blockSite");
                m.blockClock = o.getInt("blockClock");
                break;
            case CURSOR:
                m.cursorPosition   = o.getInt("cursorPosition");
                m.cursorBlockSite  = o.getInt("cursorBlockSite");
                m.cursorBlockClock = o.getInt("cursorBlockClock");
                break;
            case INIT:
            case ERROR:
                m.payload = o.optString("payload", null);
                break;
            default:
                break;
        }
        return m;
    }
    // ─── reconstruct a CRDTOperation from a network message ───────────────
    public CRDTOperation toCRDTOperation() {
        CharacterId charId   = new CharacterId(charSite, charClock);
        CharacterId parentId = new CharacterId(parentSite, parentClock);
        CharacterId blockId  = new CharacterId(blockSite, blockClock);
        CharacterId bParent  = new CharacterId(blockParentSite, blockParentClock);
        switch (type) {
           case INSERT_CHAR:
            // Pass the bold and italic network payload values into the CRDT
            return CRDTOperation.insertChar(siteId, charId, charValue, parentId, blockId, bold, italic);
            case DELETE_CHAR:
                return CRDTOperation.deleteChar(siteId, charId, blockId);
            case FORMAT_CHAR:
                return CRDTOperation.formatChar(siteId, charId, blockId,
                        bold, italic, false, false);
            case INSERT_BLOCK:
                return CRDTOperation.insertBlock(siteId, blockId, bParent);
            case DELETE_BLOCK:
                return CRDTOperation.deleteBlock(siteId, blockId);
            default:
                return null;
        }
    }
    // ─── getters ─────────────────────────────────────────────────────────
    public Type   getType()            { return type;            }
    public int    getSiteId()          { return siteId;          }
    public String getDocumentId()      { return documentId;      }
    public int    getCursorPosition()  { return cursorPosition;  }
    public int    getCursorBlockSite() { return cursorBlockSite; }
    public int    getCursorBlockClock(){ return cursorBlockClock;}
    public int    getBlockSite()       { return blockSite;       }
    public int    getBlockClock()      { return blockClock;      }
    public boolean isBold()            { return bold;            }
    public boolean isItalic()          { return italic;          }
    public String getPayload()         { return payload;         }
    public void   setPayload(String p) { payload = p;            }
}
