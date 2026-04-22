import java.util.ArrayList;
import java.util.List;

/**
 * Handles copy-paste operations with formatting.
 */
public class CopyPasteOperation {
    private final BlockCRDT blockCRDT;
    private final int siteId;
    private int clock;

    public CopyPasteOperation(BlockCRDT blockCRDT, int siteId, int clock) {
        this.blockCRDT = blockCRDT;
        this.siteId = siteId;
        this.clock = clock;
    }

    /**
     * Copies a range of characters from one block and pastes them into another block.
     * 
     * @param sourceBlockId   Block to copy from
     * @param startPos        Start position in source block (0-based)
     * @param endPos          End position in source block (exclusive)
     * @param targetBlockId   Block to paste into
     * @param insertPos       Position to insert at in target block (0-based)
     * @return true if copy-paste successful, false otherwise
     */
    public boolean copyPasteCharacters(CharacterId sourceBlockId, int startPos, int endPos,
                                       CharacterId targetBlockId, int insertPos) {
        // Get source and target blocks
        BlockNode sourceBlock = blockCRDT.getBlock(sourceBlockId);
        BlockNode targetBlock = blockCRDT.getBlock(targetBlockId);

        // Validate blocks exist and are not deleted
        if (sourceBlock == null || targetBlock == null || 
            sourceBlock.isDeleted() || targetBlock.isDeleted()) {
            return false;
        }

        // Get visible characters from source block
        CharacterCRDT sourceContent = sourceBlock.getContent();
        List<CharacterNode> sourceChars = sourceContent.getVisibleNodes();

        // Validate positions
        if (startPos < 0 || endPos > sourceChars.size() || startPos >= endPos) {
            return false;
        }

        // Get the range of characters to copy
        List<CharacterNode> charsToCopy = sourceChars.subList(startPos, endPos);

        // Get target content and determine parent ID for insertion
        CharacterCRDT targetContent = targetBlock.getContent();
        List<CharacterNode> targetChars = targetContent.getVisibleNodes();

        // Determine parent ID based on insert position
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        if (insertPos > 0 && insertPos <= targetChars.size()) {
            parentId = targetChars.get(insertPos - 1).getId();
        } else if (insertPos > targetChars.size()) {
            insertPos = targetChars.size();
            if (insertPos > 0) {
                parentId = targetChars.get(insertPos - 1).getId();
            }
        }

        // Paste characters with formatting preserved
        for (CharacterNode charNode : charsToCopy) {
            clock++;
            targetContent.insert(
                siteId,
                clock,
                charNode.getValue(),
                parentId,
                targetBlockId,
                charNode.isBold(),
                charNode.isItalic()
            );
            parentId = new CharacterId(siteId, clock);
        }

        return true;
    }

    /**
     * Copies an entire block (with all its characters and formatting) and pastes it as a new block.
     * 
     * @param sourceBlockId   Block to copy from
     * @param newParentId     Parent for the new copied block
     * @return CharacterId of the new pasted block, or null if failed
     */
    public CharacterId copyPasteBlock(CharacterId sourceBlockId, CharacterId newParentId) {
        // Get source block
        BlockNode sourceBlock = blockCRDT.getBlock(sourceBlockId);

        // Validate block exists and is not deleted
        if (sourceBlock == null || sourceBlock.isDeleted()) {
            return null;
        }

        // Create new block
        clock++;
        CRDTOperation insertOp = blockCRDT.insertBlock(siteId, clock, newParentId);
        
        if (insertOp == null) {
            return null;
        }

        CharacterId newBlockId = insertOp.getBlockId();
        BlockNode newBlock = blockCRDT.getBlock(newBlockId);

        if (newBlock == null) {
            return null;
        }

        // Copy all characters from source block to new block
        CharacterCRDT sourceContent = sourceBlock.getContent();
        CharacterCRDT newContent = newBlock.getContent();

        List<CharacterNode> sourceChars = sourceContent.getVisibleNodes();
        CharacterId parentId = CharacterCRDT.ROOT_ID;

        for (CharacterNode charNode : sourceChars) {
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

        return newBlockId;
    }

    /**
     * Copies multiple consecutive blocks and pastes them after a target block.
     * 
     * @param sourceBlockIds  List of block IDs to copy (in order)
     * @param afterBlockId    Insert copied blocks after this block (null for root)
     * @return List of new block IDs, or empty list if failed
     */
    public List<CharacterId> copyPasteBlocks(List<CharacterId> sourceBlockIds, CharacterId afterBlockId) {
        List<CharacterId> newBlockIds = new ArrayList<>();

        // Start with afterBlockId as parent
        CharacterId currentParent = afterBlockId;

        for (CharacterId sourceBlockId : sourceBlockIds) {
            CharacterId newBlockId = copyPasteBlock(sourceBlockId, currentParent);
            
            if (newBlockId == null) {
                // If any copy fails, rollback all
                return new ArrayList<>();
            }

            newBlockIds.add(newBlockId);
            // Next block becomes child of this one
            currentParent = newBlockId;
        }

        return newBlockIds;
    }

    /**
     * Pastes plain text as characters into a block, creating new CharacterNodes.
     * Used for pasting text from external sources.
     * 
     * @param text        Text to paste
     * @param blockId     Target block
     * @param insertPos   Position to insert at
     * @return true if paste successful, false otherwise
     */
    public boolean pasteText(String text, CharacterId blockId, int insertPos) {
        BlockNode block = blockCRDT.getBlock(blockId);
        
        if (block == null || block.isDeleted()) {
            return false;
        }

        CharacterCRDT content = block.getContent();
        List<CharacterNode> visibleChars = content.getVisibleNodes();

        // Determine parent ID
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        if (insertPos > 0 && insertPos <= visibleChars.size()) {
            parentId = visibleChars.get(insertPos - 1).getId();
        } else if (insertPos > visibleChars.size()) {
            insertPos = visibleChars.size();
            if (insertPos > 0) {
                parentId = visibleChars.get(insertPos - 1).getId();
            }
        }

        // Insert each character
        for (char c : text.toCharArray()) {
            clock++;
            content.insert(siteId, clock, c, parentId, blockId, false, false);
            parentId = new CharacterId(siteId, clock);
        }

        return true;
    }

    /**
     * Gets the current clock value (for external tracking if needed).
     */
    public int getClock() {
        return clock;
    }

    /**
     * Updates the clock value (called after operations to keep it in sync).
     */
    public void setClock(int newClock) {
        this.clock = newClock;
    }
}
