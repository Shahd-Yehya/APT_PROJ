package repository;
import model.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CharacterCRDT {

    

    private static final int MAX_HISTORY = 10;

    

    
    public static final CharacterId ROOT_ID = new CharacterId(-1, -1);
    private final CharacterNode root;

    

    
    private final ConcurrentHashMap<CharacterId, CharacterNode> nodeIndex =
            new ConcurrentHashMap<>();

    

    private final Deque<CRDTOperation> undoStack = new ArrayDeque<>();
    private final Deque<CRDTOperation> redoStack = new ArrayDeque<>();

    

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    

    public CharacterCRDT() {
        
        root = new CharacterNode(ROOT_ID, '\0', null);
        nodeIndex.put(ROOT_ID, root);
    }

    
    
    

    
    public CRDTOperation insert(int siteId, int clock, char value,
                                CharacterId parentId, CharacterId blockId,
                                boolean bold, boolean italic) {
        CharacterId newId = new CharacterId(siteId, clock);

        
        if (nodeIndex.containsKey(newId)) {
            return null; 
        }

        CharacterNode newNode = new CharacterNode(newId, value, parentId);
        newNode.setBold(bold);
        newNode.setItalic(italic);

        
        CharacterNode parent = nodeIndex.getOrDefault(parentId, root);

        
        parent.addChild(newNode);
        nodeIndex.put(newId, newNode);

               // Pass the formatting arguments to the updated factory method
    CRDTOperation op = CRDTOperation.insertChar(siteId, newId, value, parentId, blockId, bold, italic);
    pushUndo(op);
    return op;
    }

    
    public void applyRemoteInsert(CRDTOperation op, boolean bold, boolean italic) {
        CharacterId newId    = op.getCharId();
        CharacterId parentId = op.getParentId();

        if (nodeIndex.containsKey(newId)) return; 

        CharacterNode newNode = new CharacterNode(newId, op.getValue(), parentId);
        newNode.setBold(bold);
        newNode.setItalic(italic);

        CharacterNode parent = nodeIndex.getOrDefault(parentId, root);
        parent.addChild(newNode);
        nodeIndex.put(newId, newNode);
    }

    
    public CRDTOperation delete(CharacterId charId, CharacterId blockId, int siteId) {
        CharacterNode node = nodeIndex.get(charId);
        if (node == null || node.isDeleted()) return null; 

        node.setDeleted(true);

        CRDTOperation op = CRDTOperation.deleteChar(siteId, charId, blockId);
        pushUndo(op);
        return op;
    }

    
    public void applyRemoteDelete(CRDTOperation op) {
        CharacterNode node = nodeIndex.get(op.getCharId());
        if (node != null) node.setDeleted(true);
    }

    
    public CRDTOperation format(CharacterId charId, CharacterId blockId,
                                int siteId, boolean bold, boolean italic) {
        CharacterNode node = nodeIndex.get(charId);
        if (node == null) return null;

        boolean prevBold   = node.isBold();
        boolean prevItalic = node.isItalic();

        node.setBold(bold);
        node.setItalic(italic);

        CRDTOperation op = CRDTOperation.formatChar(siteId, charId, blockId,
                bold, italic, prevBold, prevItalic);
        pushUndo(op);
        return op;
    }

    
    public void applyRemoteFormat(CRDTOperation op) {
        CharacterNode node = nodeIndex.get(op.getCharId());
        if (node != null) {
            node.setBold(op.isBold());
            node.setItalic(op.isItalic());
        }
    }

    

    
    public CRDTOperation undo(CharacterId blockId) {
        rwLock.writeLock().lock();
        try {
            if (undoStack.isEmpty()) return null;

            CRDTOperation original = undoStack.pop();
            CRDTOperation inverse  = original.inverse();
            applyOperationInternal(inverse);

            redoStack.push(original);
            return inverse;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    
    public CRDTOperation redo(CharacterId blockId) {
        rwLock.writeLock().lock();
        try {
            if (redoStack.isEmpty()) return null;

            CRDTOperation original = redoStack.pop();
            applyOperationInternal(original);

            undoStack.push(original);
            return original;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    

    
    public String getVisibleText() {
        rwLock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            dfsCollect(root, sb);
            return sb.toString();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    
    public List<CharacterNode> getVisibleNodes() {
        rwLock.readLock().lock();
        try {
            List<CharacterNode> result = new ArrayList<>();
            dfsCollectNodes(root, result);
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    
    public CharacterId getParentIdForCursor(int cursorPos) {
        rwLock.readLock().lock();
        try {
            List<CharacterNode> visible = new ArrayList<>();
            dfsCollectNodes(root, visible);
            
            // Clamp to valid range [0, visible.size()]
            if (cursorPos < 0) cursorPos = 0;
            if (cursorPos > visible.size()) cursorPos = visible.size();
            
            // If at the start or block is empty, return ROOT_ID
            if (cursorPos == 0) return ROOT_ID;
            
            return visible.get(cursorPos - 1).getId();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    
    public int visibleLength() {
        rwLock.readLock().lock();
        try {
            int[] count = {0};
            dfsCount(root, count);
            return count[0];
        } finally {
            rwLock.readLock().unlock();
        }
    }

    

    
    CharacterNode getNode(CharacterId id) {
        return nodeIndex.get(id);
    }

    
    
    

    
    private void dfsCollect(CharacterNode node, StringBuilder sb) {
        
        if (node != root && !node.isDeleted()) {
            sb.append(node.getValue());
        }
        for (CharacterNode child : node.getChildrenSnapshot()) {
            dfsCollect(child, sb);
        }
    }

    
    private void dfsCollectNodes(CharacterNode node, List<CharacterNode> out) {
        if (node != root && !node.isDeleted()) {
            out.add(node);
        }
        for (CharacterNode child : node.getChildrenSnapshot()) {
            dfsCollectNodes(child, out);
        }
    }

    
    private void dfsCount(CharacterNode node, int[] count) {
        if (node != root && !node.isDeleted()) count[0]++;
        for (CharacterNode child : node.getChildrenSnapshot()) {
            dfsCount(child, count);
        }
    }

    
    private void applyOperationInternal(CRDTOperation op) {
        switch (op.getType()) {
            case INSERT_CHAR: {
                CharacterId newId = op.getCharId();
                CharacterNode existing = nodeIndex.get(newId);
                if (existing != null) {
                    // Redo case: node exists but was marked deleted by undo
                    existing.setDeleted(false);
                    existing.setBold(op.isBold());
                    existing.setItalic(op.isItalic());
                } else {
                    CharacterNode newNode = new CharacterNode(
                            newId, op.getValue(), op.getParentId());
                    newNode.setBold(op.isBold());
                    newNode.setItalic(op.isItalic());
                    CharacterNode parent = nodeIndex.getOrDefault(
                            op.getParentId(), root);
                    parent.addChild(newNode);
                    nodeIndex.put(newId, newNode);
                }
                break;
            }

            case DELETE_CHAR: {
                CharacterNode n = nodeIndex.get(op.getCharId());
                if (n != null) n.setDeleted(true);
                break;
            }

            case UNDELETE_CHAR: {
                CharacterNode n = nodeIndex.get(op.getCharId());
                if (n != null) n.setDeleted(false);
                break;
            }

            case FORMAT_CHAR: {
                CharacterNode n = nodeIndex.get(op.getCharId());
                if (n != null) {
                    n.setBold(op.isBold());
                    n.setItalic(op.isItalic());
                }
                break;
            }

            default:
                
                break;
        }
    }

    
    private void pushUndo(CRDTOperation op) {
        rwLock.writeLock().lock();
        try {
            undoStack.push(op);
            if (undoStack.size() > MAX_HISTORY) {
                
                ((ArrayDeque<CRDTOperation>) undoStack).removeLast();
            }
            redoStack.clear(); 
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
