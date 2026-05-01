package service;
import model.*;
import repository.*;
import java.util.List;

/**
 * Combines two adjacent blocks into one.
 */
public class MergeBlockOperation {
    private final BlockCRDT blockCRDT;
    private final int siteId;
    private int clock;

    public MergeBlockOperation(BlockCRDT blockCRDT, int siteId, int clock) {
        this.blockCRDT = blockCRDT;
        this.siteId = siteId;
        this.clock = clock;
    }

    /**
     * Merges twoBlocks (firstBlockId and secondBlockId).
     * All characters from secondBlock are moved to firstBlock.
     * secondBlock is then deleted.
     * 
     * @param firstBlockId  ID of the block to merge into
     * @param secondBlockId ID of the block to merge from (will be deleted)
     * @return true if merge successful, false otherwise
     */
    public boolean mergeBlocks(CharacterId firstBlockId, CharacterId secondBlockId) {
        // Get the blocks
        BlockNode firstBlock = blockCRDT.getBlock(firstBlockId);
        BlockNode secondBlock = blockCRDT.getBlock(secondBlockId);

        // Validate blocks exist and are not deleted
        if (firstBlock == null || secondBlock == null || 
            firstBlock.isDeleted() || secondBlock.isDeleted()) {
            return false;
        }

        // Get content from both blocks
        CharacterCRDT firstContent = firstBlock.getContent();
        CharacterCRDT secondContent = secondBlock.getContent();

        // Get all visible characters from the second block
        List<CharacterNode> secondChars = secondContent.getVisibleNodes();

        // Determine the parent ID for insertion in first block
        // (should be the last character in first block)
        List<CharacterNode> firstChars = firstContent.getVisibleNodes();
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        if (!firstChars.isEmpty()) {
            parentId = firstChars.get(firstChars.size() - 1).getId();
        }

        // Move all characters from second block to first block
        for (CharacterNode charNode : secondChars) {
            clock++;
            
            // Insert character into first block with formatting preserved
            firstContent.insert(
                siteId,
                clock,
                charNode.getValue(),
                parentId,
                firstBlockId,
                charNode.isBold(),
                charNode.isItalic()
            );
            
            // The newly inserted character becomes the parent for the next one
            parentId = new CharacterId(siteId, clock);
            
            // Delete from second block (tombstone)
            secondContent.delete(charNode.getId(), secondBlockId, siteId);
        }

        // Delete the second block (tombstone deletion)
        clock++;
        blockCRDT.deleteBlock(secondBlockId, siteId);

        return true;
    }
}
