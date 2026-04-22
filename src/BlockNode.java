package src;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BlockNode {

    private final CharacterId blockId;
    private final CharacterId parentId;
    private volatile boolean deleted;
    private final CharacterCRDT content;
    private final List<BlockNode> children = new ArrayList<>();

    public static final Comparator<BlockNode> CHILD_ORDER =
            Comparator.comparingInt((BlockNode n) -> n.getBlockId().getClock())
                      .reversed()
                      .thenComparingInt(n -> n.getBlockId().getSiteId());

    public BlockNode(CharacterId blockId, CharacterId parentId) {
        this.blockId = blockId;
        this.parentId = parentId;
        this.deleted = false;
        this.content = new CharacterCRDT();
    }

    public synchronized void addChild(BlockNode child) {
        int pos = binarySearchInsertionPoint(child);
        children.add(pos, child);
    }

    public synchronized List<BlockNode> getChildrenSnapshot() {
        return new ArrayList<>(children);
    }

    public synchronized int childCount() {
        return children.size();
    }

    private int binarySearchInsertionPoint(BlockNode newChild) {
        int lo = 0;
        int hi = children.size();

        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (CHILD_ORDER.compare(children.get(mid), newChild) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        return lo;
    }

    public CharacterId getBlockId() {
        return blockId;
    }

    public CharacterId getParentId() {
        return parentId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public CharacterCRDT getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "BlockNode{id=" + blockId
                + ", parent=" + parentId
                + (deleted ? ", DELETED" : "")
                + ", visibleText=\"" + content.getVisibleText() + "\""
                + "}";
    }
}