/**
 * Moves a block to a new parent position.
 */
public class MoveBlockOperation {
    private final BlockCRDT blockCRDT;
    private final int siteId;
    private int clock;

    public MoveBlockOperation(BlockCRDT blockCRDT, int siteId, int clock) {
        this.blockCRDT = blockCRDT;
        this.siteId = siteId;
        this.clock = clock;
    }

    /**
     * Moves a block to a new parent position.
     * 
     * Technically in CRDT, we don't "move" blocks - we create a new block
     * with the same content at the new position and tombstone the old one.
     * However, this simplified version changes the parent reference.
     * 
     * For a true CRDT move, you would:
     * 1. Create a new block as child of newParentId with same content
     * 2. Tombstone the original block
     * 
     * This approach is simpler and works for the project requirements.
     * 
     * @param blockIdToMove   The ID of the block to move
     * @param newParentId     The ID of the new parent block (or null for root)
     * @return true if move successful, false otherwise
     */
    public boolean moveBlock(CharacterId blockIdToMove, CharacterId newParentId) {
        // Get the block to move
        BlockNode blockToMove = blockCRDT.getBlock(blockIdToMove);
        
        // Validate the block exists and is not deleted
        if (blockToMove == null || blockToMove.isDeleted()) {
            return false;
        }

        // Prevent circular references: block cannot become its own ancestor
        if (isAncestor(blockIdToMove, newParentId)) {
            return false;
        }

        // In a true CRDT, we would create a new block with same content
        // For simplicity, we're updating the parent reference
        // This works because BlockCRDT uses parent-child relationships
        // and the ordering is deterministic
        
        // Create new block with same content at new position
        clock++;
        CharacterId newBlockId = new CharacterId(siteId, clock);
        
        // Insert new block as child of newParentId
        blockCRDT.insertBlock(siteId, clock, newParentId);
        
        // Get the newly created block
        BlockNode newBlock = blockCRDT.getBlock(newBlockId);
        if (newBlock == null) {
            return false;
        }

        // Copy all characters from original block to new block
        CharacterCRDT originalContent = blockToMove.getContent();
        CharacterCRDT newContent = newBlock.getContent();
        
        java.util.List<CharacterNode> visibleChars = originalContent.getVisibleNodes();
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        
        for (CharacterNode charNode : visibleChars) {
            clock++;
            newContent.insert(
                siteId,
                clock,
                charNode.getValue(),
                parentId,
                newBlockId,
                charNode.isBold(),
                charNode.isItalic()
            );
            parentId = new CharacterId(siteId, clock);
        }

        // Tombstone the original block
        blockCRDT.deleteBlock(blockIdToMove, siteId);

        return true;
    }

    /**
     * Checks if targetId is an ancestor of blockId.
     * Used to prevent circular references.
     * 
     * @param blockId   The block to check ancestors for
     * @param targetId  The potential ancestor
     * @return true if targetId is an ancestor of blockId
     */
    private boolean isAncestor(CharacterId blockId, CharacterId targetId) {
        if (targetId == null) {
            return false; // null parent (root) is never a circular reference
        }

        BlockNode current = blockCRDT.getBlock(blockId);
        
        while (current != null) {
            CharacterId parentId = current.getParentId();
            
            if (parentId == null) {
                return false; // reached root without finding targetId
            }
            
            if (parentId.equals(targetId)) {
                return true; // found targetId as ancestor
            }
            
            current = blockCRDT.getBlock(parentId);
        }
        
        return false;
    }
}
