import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CursorTracker {
    private final String documentId;
    private final ConcurrentHashMap<String, UserCursor> userCursors;
    private int colorCounter = 0;
    private static final String[] CURSOR_COLORS = {
        "RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE", "CYAN", "MAGENTA"
    };

    public CursorTracker(String documentId) {
        this.documentId = documentId;
        this.userCursors = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new user's cursor in the document.
     */
    public void registerUserCursor(String userId, int initialPos, CharacterId blockId) {
        String color = CURSOR_COLORS[colorCounter % CURSOR_COLORS.length];
        colorCounter++;

        UserCursor cursor = new UserCursor(userId, initialPos, blockId, color);
        userCursors.put(userId, cursor);

        System.out.println("Cursor registered for user " + userId + 
            " at position " + initialPos + " in block " + blockId);
    }

    /**
     * Unregisters a user's cursor (when they disconnect).
     */
    public void unregisterUserCursor(String userId) {
        UserCursor removed = userCursors.remove(userId);
        if (removed != null) {
            System.out.println("Cursor unregistered for user " + userId);
        }
    }

    /**
     * Updates a user's cursor position locally.
     */
    public void updateCursorPosition(String userId, int newPos, CharacterId blockId) {
        UserCursor cursor = userCursors.get(userId);
        if (cursor != null) {
            cursor.setPosition(newPos);
            cursor.setBlockId(blockId);
            cursor.updateTimestamp();
        }
    }

    /**
     * Gets cursor information for a specific user.
     */
    public UserCursor getUserCursor(String userId) {
        return userCursors.get(userId);
    }

    /**
     * Gets all active user cursors.
     */
    public Collection<UserCursor> getAllUserCursors() {
        return new ArrayList<>(userCursors.values());
    }

    /**
     * Gets cursor information for all users except one.
     */
    public Collection<UserCursor> getOtherUserCursors(String userId) {
        Collection<UserCursor> all = new ArrayList<>(userCursors.values());
        all.removeIf(cursor -> cursor.getUserId().equals(userId));
        return all;
    }

    /**
     * Adjusts cursor positions when a character is inserted.
     * All cursors after the insertion point move forward by 1.
     */
    public void adjustCursorsOnCharInsert(CharacterId blockId, int insertPos, String siteId) {
        for (UserCursor cursor : userCursors.values()) {
            // Only adjust if cursor is in the same block and after the insert position
            if (cursor.getBlockId().equals(blockId) && cursor.getPosition() >= insertPos) {
                cursor.setPosition(cursor.getPosition() + 1);
            }
        }
    }

    /**
     * Adjusts cursor positions when a character is deleted.
     * All cursors after the deletion point move backward by 1.
     */
    public void adjustCursorsOnCharDelete(CharacterId blockId, int deletePos, String siteId) {
        for (UserCursor cursor : userCursors.values()) {
            // Only adjust if cursor is in the same block
            if (cursor.getBlockId().equals(blockId)) {
                if (cursor.getPosition() > deletePos) {
                    // Cursor is after deleted position, move back
                    cursor.setPosition(cursor.getPosition() - 1);
                } else if (cursor.getPosition() == deletePos) {
                    // Cursor was at deleted position, keep at same visual position
                    // (which is now pointing to the next character)
                }
            }
        }
    }

    /**
     * Adjusts cursor positions when a block is inserted.
     * Cursors in subsequent blocks might be affected depending on hierarchy.
     */
    public void adjustCursorsOnBlockInsert(CharacterId newBlockId, CharacterId parentBlockId) {
        // If a block is inserted as child of a parent,
        // cursors in the parent block might need adjustment
        for (UserCursor cursor : userCursors.values()) {
            if (cursor.getBlockId().equals(parentBlockId)) {
                // Cursor could move to point to the new block if needed
                // For now, keep cursor in original position
            }
        }
    }

    /**
     * Adjusts cursor positions when a block is deleted.
     * Cursors in deleted blocks are moved to the block's parent.
     */
    public void adjustCursorsOnBlockDelete(CharacterId deletedBlockId, CharacterId parentBlockId) {
        for (UserCursor cursor : userCursors.values()) {
            if (cursor.getBlockId().equals(deletedBlockId)) {
                // Move cursor to parent block
                cursor.setBlockId(parentBlockId);
                cursor.setPosition(0); // Move to start of parent
            }
        }
    }

    /**
     * Adjusts cursor positions when blocks are merged.
     * Cursors from the merged block are moved to the target block with adjusted position.
     */
    public void adjustCursorsOnBlockMerge(CharacterId sourceBlock, CharacterId targetBlock, 
                                          int targetBlockLength) {
        for (UserCursor cursor : userCursors.values()) {
            if (cursor.getBlockId().equals(sourceBlock)) {
                // Move cursor to target block, at offset = targetBlockLength + cursor.position
                cursor.setBlockId(targetBlock);
                cursor.setPosition(targetBlockLength + cursor.getPosition());
            }
        }
    }

    /**
     * Adjusts cursor positions when a block is split.
     * Cursors in the right part are moved to the new block.
     */
    public void adjustCursorsOnBlockSplit(CharacterId originalBlock, CharacterId newBlock, 
                                          int splitPos) {
        for (UserCursor cursor : userCursors.values()) {
            if (cursor.getBlockId().equals(originalBlock)) {
                if (cursor.getPosition() >= splitPos) {
                    // Cursor is in the right part, move to new block
                    cursor.setBlockId(newBlock);
                    cursor.setPosition(cursor.getPosition() - splitPos);
                }
                // If cursor is in left part, keep in original block
            }
        }
    }

    /**
     * Gets formatted cursor information for broadcast to clients.
     */
    public String getCursorInfo(String userId) {
        UserCursor cursor = userCursors.get(userId);
        if (cursor != null) {
            return cursor.toString();
        }
        return null;
    }

    /**
     * Gets cursor information for all active users (for broadcasting).
     */
    public List<String> getAllCursorInfo() {
        List<String> result = new ArrayList<>();
        for (UserCursor cursor : userCursors.values()) {
            result.add(cursor.toString());
        }
        return result;
    }
}

/**
 * Represents a user's cursor position and metadata.
 */
class UserCursor {
    private final String userId;
    private int position;
    private CharacterId blockId;
    private final String color;
    private long lastUpdated;

    public UserCursor(String userId, int position, CharacterId blockId, String color) {
        this.userId = userId;
        this.position = position;
        this.blockId = blockId;
        this.color = color;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getUserId() { return userId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public CharacterId getBlockId() { return blockId; }
    public void setBlockId(CharacterId blockId) { this.blockId = blockId; }
    public String getColor() { return color; }
    public long getLastUpdated() { return lastUpdated; }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "UserCursor{" +
                "userId='" + userId + '\'' +
                ", position=" + position +
                ", blockId=" + blockId +
                ", color='" + color + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }

    /**
     * Serializes cursor to a format suitable for network transmission.
     */
    public String toNetworkFormat() {
        return userId + "|" + position + "|" + blockId + "|" + color;
    }
}
