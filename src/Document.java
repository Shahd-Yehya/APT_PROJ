package src;
import java.util.List;

public class Document {
    private final BlockCRDT block;

    public Document(BlockCRDT block) {
        this.block = block;
    }

// insert bock after parent block id
// lw 3ayez t insert block as first block pass parent block id as null
    public CRDTOperation insertBlock(int siteId, int clock, CharacterId parentBlockId) {
        return block.insertBlock(siteId, clock, parentBlockId);
    }

// remote insert from another user
    public void applyRemoteInsertBlock(CRDTOperation op) {
        block.applyRemoteInsertBlock(op);
    }

// delete block by id
    public CRDTOperation deleteBlock(CharacterId blockId, int siteId) {
        return block.deleteBlock(blockId, siteId);
    }

// remote delete from another user
    public void applyRemoteDeleteBlock(CRDTOperation op) {
        block.applyRemoteDeleteBlock(op);
    }

// Get visible blocks for SplitBlockOperation
    public List<BlockNode> getVisibleBlocks() {
        return block.getVisibleBlocks();
    }
    
// get full doc text
    public String getFullText() {
        return block.getVisibleDocumentText();
    }

// get specific block for SplitBlockOperation
    public BlockNode getBlock(CharacterId blockId) {
        return block.getBlock(blockId);
    }

// needed for tests and SplitBlockOperation
    public BlockCRDT getBlockCRDT() {
        return block;
    }

}