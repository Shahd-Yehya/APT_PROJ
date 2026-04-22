package src;
import java.util.List;

public class SplitBlockOperation {
    private final BlockCRDT block;
    private final int siteId;
    private int clock;

    public SplitBlockOperation(BlockCRDT block, int siteId, int clock) {
        this.block = block;
        this.siteId = siteId;
        this.clock = clock;
    }
    
    public void splitBlock(CharacterId blockId, int cursorPos) {
        //hageeb el original block elly h3mlo split 
        BlockNode originalBlock = block.getBlock(blockId);
        
        // lazem at2akd en el block not null and not deleted
        if (originalBlock == null || originalBlock.isDeleted()) {
            return;
        }
        
        // get all the visible chars from the original block
        // w a3mel sublist mn el cursor position l a5er el list 
        List<CharacterNode> visibleChars = originalBlock.getContent().getVisibleNodes();
        List<CharacterNode> rightPart = visibleChars.subList(cursorPos, visibleChars.size());

        // insert new block after the original one 
        clock++;
        CRDTOperation insertOp = block.insertBlock(siteId, clock, blockId);
        
        // get the new block id and the new block node
        CharacterId newBlockId = insertOp.getBlockId();
        BlockNode newBlock = block.getBlock(newBlockId);
         
        // get the parent of the first char in the right block to use it in the loop 
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        
        for (CharacterNode node : rightPart) {
        // awl 7aga hn3ml delete ll right part elly hn7to f block gded
        originalBlock.getContent().delete(node.getId(), blockId, siteId);
        
        // b3d kda h insert kol char mn el right part fe el block el gded bl formatting bta3o
        clock++;
            newBlock.getContent().insert(
                siteId,
                clock,
                node.getValue(),    
                parentId,           
                newBlockId,         
                node.isBold(),      
                node.isItalic()     
            );
        // el char elly lesa m3mlo insert hyb2a parent lly b3do
        parentId = new CharacterId(siteId, clock);
        }
    }
}