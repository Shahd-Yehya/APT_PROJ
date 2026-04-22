package src;

public class CRDTOperation {

    

    public enum Type {
        INSERT_CHAR,
        DELETE_CHAR,
        UNDELETE_CHAR,
        FORMAT_CHAR,
        INSERT_BLOCK,
        DELETE_BLOCK,
        SPLIT_BLOCK
    }

    

    private final Type         type;

    
    private final CharacterId  charId;       
    private final CharacterId  parentId;     
    private final char         value;        
    private final CharacterId  blockId;      

    
    private final boolean      bold;
    private final boolean      italic;
    private final boolean      prevBold;     
    private final boolean      prevItalic;

    
    private final CharacterId  blockParentId; 

    
    private final int          siteId;

    

    private CRDTOperation(Builder b) {
        this.type          = b.type;
        this.charId        = b.charId;
        this.parentId      = b.parentId;
        this.value         = b.value;
        this.blockId       = b.blockId;
        this.bold          = b.bold;
        this.italic        = b.italic;
        this.prevBold      = b.prevBold;
        this.prevItalic    = b.prevItalic;
        this.blockParentId = b.blockParentId;
        this.siteId        = b.siteId;
    }

    

    
    public static CRDTOperation insertChar(int siteId, CharacterId charId,
                                           char value, CharacterId parentId,
                                           CharacterId blockId) {
        return new Builder(Type.INSERT_CHAR)
                .siteId(siteId).charId(charId).value(value)
                .parentId(parentId).blockId(blockId)
                .build();
    }

    
    public static CRDTOperation deleteChar(int siteId, CharacterId charId,
                                           CharacterId blockId) {
        return new Builder(Type.DELETE_CHAR)
                .siteId(siteId).charId(charId).blockId(blockId)
                .build();
    }

    
    public static CRDTOperation undeleteChar(int siteId, CharacterId charId,
                                             CharacterId blockId) {
        return new Builder(Type.UNDELETE_CHAR)
                .siteId(siteId).charId(charId).blockId(blockId)
                .build();
    }

    
    public static CRDTOperation formatChar(int siteId, CharacterId charId,
                                           CharacterId blockId,
                                           boolean bold, boolean italic,
                                           boolean prevBold, boolean prevItalic) {
        return new Builder(Type.FORMAT_CHAR)
                .siteId(siteId).charId(charId).blockId(blockId)
                .bold(bold).italic(italic)
                .prevBold(prevBold).prevItalic(prevItalic)
                .build();
    }

    
    public static CRDTOperation insertBlock(int siteId, CharacterId blockId,
                                            CharacterId blockParentId) {
        return new Builder(Type.INSERT_BLOCK)
                .siteId(siteId).blockId(blockId).blockParentId(blockParentId)
                .build();
    }

    
    public static CRDTOperation deleteBlock(int siteId, CharacterId blockId) {
        return new Builder(Type.DELETE_BLOCK)
                .siteId(siteId).blockId(blockId)
                .build();
    }

    

    
    public CRDTOperation inverse() {
        switch (type) {
            case INSERT_CHAR:
                return deleteChar(siteId, charId, blockId);
            case DELETE_CHAR:
                return undeleteChar(siteId, charId, blockId);
            case UNDELETE_CHAR:
                return deleteChar(siteId, charId, blockId);
            case FORMAT_CHAR:
                return formatChar(siteId, charId, blockId,
                        prevBold, prevItalic, bold, italic);
            case INSERT_BLOCK:
                return deleteBlock(siteId, blockId);
            case DELETE_BLOCK:
                return insertBlock(siteId, blockId, blockParentId);
            default:
                throw new UnsupportedOperationException(
                        "No inverse defined for op type: " + type);
        }
    }

    

    public Type         getType()          { return type;          }
    public CharacterId  getCharId()        { return charId;        }
    public CharacterId  getParentId()      { return parentId;      }
    public char         getValue()         { return value;         }
    public CharacterId  getBlockId()       { return blockId;       }
    public boolean      isBold()           { return bold;          }
    public boolean      isItalic()         { return italic;        }
    public boolean      isPrevBold()       { return prevBold;      }
    public boolean      isPrevItalic()     { return prevItalic;    }
    public CharacterId  getBlockParentId() { return blockParentId; }
    public int          getSiteId()        { return siteId;        }

    @Override
    public String toString() {
        return "CRDTOp{" + type + ", char=" + charId + ", block=" + blockId + "}";
    }

    

    private static class Builder {
        Type         type;
        CharacterId  charId;
        CharacterId  parentId;
        char         value;
        CharacterId  blockId;
        boolean      bold, italic, prevBold, prevItalic;
        CharacterId  blockParentId;
        int          siteId;

        Builder(Type type)           { this.type = type; }
        Builder siteId(int v)        { siteId = v;          return this; }
        Builder charId(CharacterId v){ charId = v;          return this; }
        Builder parentId(CharacterId v){ parentId = v;      return this; }
        Builder value(char v)        { value = v;           return this; }
        Builder blockId(CharacterId v){ blockId = v;        return this; }
        Builder bold(boolean v)      { bold = v;            return this; }
        Builder italic(boolean v)    { italic = v;          return this; }
        Builder prevBold(boolean v)  { prevBold = v;        return this; }
        Builder prevItalic(boolean v){ prevItalic = v;      return this; }
        Builder blockParentId(CharacterId v){ blockParentId = v; return this; }
        CRDTOperation build()        { return new CRDTOperation(this); }
    }
}
