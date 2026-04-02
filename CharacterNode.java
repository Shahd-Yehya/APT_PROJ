import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CharacterNode {

    

    
    private final CharacterId id;

    
    private final char value;

    
    private CharacterId parentId;

    

    
    private boolean deleted;

    

    private boolean bold;
    private boolean italic;

    

    
    private final List<CharacterNode> children = new ArrayList<>();

    
    public static final Comparator<CharacterNode> CHILD_ORDER =
            Comparator.comparingInt((CharacterNode n) -> n.getId().getClock())
                      .reversed()
                      .thenComparingInt(n -> n.getId().getSiteId());

    

    
    public CharacterNode(CharacterId id, char value, CharacterId parentId) {
        this.id       = id;
        this.value    = value;
        this.parentId = parentId;
        this.deleted  = false;
        this.bold     = false;
        this.italic   = false;
    }

    

    
    public synchronized void addChild(CharacterNode child) {
        int pos = binarySearchInsertionPoint(child);
        children.add(pos, child);
    }

    
    public synchronized List<CharacterNode> getChildrenSnapshot() {
        return new ArrayList<>(children);
    }

    
    public synchronized int childCount() {
        return children.size();
    }

    

    
    private int binarySearchInsertionPoint(CharacterNode newChild) {
        int lo = 0, hi = children.size();
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

    

    public CharacterId getId()        { return id;       }
    public char        getValue()     { return value;    }
    public CharacterId getParentId()  { return parentId; }

    public synchronized boolean isDeleted()                  { return deleted;        }
    public synchronized void    setDeleted(boolean deleted)  { this.deleted = deleted; }

    public synchronized boolean isBold()                     { return bold;           }
    public synchronized void    setBold(boolean bold)        { this.bold = bold;      }

    public synchronized boolean isItalic()                   { return italic;         }
    public synchronized void    setItalic(boolean italic)    { this.italic = italic;  }

    @Override
    public String toString() {
        return "CharNode{id=" + id
                + ", val='" + value + "'"
                + (deleted ? ", DELETED" : "")
                + (bold    ? ", BOLD"    : "")
                + (italic  ? ", ITALIC"  : "")
                + "}";
    }
}
