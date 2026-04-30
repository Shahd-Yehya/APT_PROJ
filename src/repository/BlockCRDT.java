package repository;
import model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockCRDT {

    public static final CharacterId ROOT_BLOCK_ID = new CharacterId(-1000, -1000);

    private final BlockNode root;
    private final ConcurrentHashMap<CharacterId, BlockNode> blockIndex =
            new ConcurrentHashMap<>();

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public BlockCRDT() {
        root = new BlockNode(ROOT_BLOCK_ID, null);
        blockIndex.put(ROOT_BLOCK_ID, root);
    }

    public CRDTOperation insertBlock(int siteId, int clock, CharacterId parentBlockId) {
        rwLock.writeLock().lock();
        try {
            CharacterId newBlockId = new CharacterId(siteId, clock);

            if (blockIndex.containsKey(newBlockId)) {
                return null;
            }

            CharacterId safeParentId = (parentBlockId == null) ? ROOT_BLOCK_ID : parentBlockId;
            BlockNode parent = blockIndex.getOrDefault(safeParentId, root);

            BlockNode newBlock = new BlockNode(newBlockId, safeParentId);
            parent.addChild(newBlock);
            blockIndex.put(newBlockId, newBlock);

            return CRDTOperation.insertBlock(siteId, newBlockId, safeParentId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void applyRemoteInsertBlock(CRDTOperation op) {
        if (op == null || op.getType() != CRDTOperation.Type.INSERT_BLOCK) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            CharacterId blockId = op.getBlockId();
            CharacterId parentId = op.getBlockParentId();

            if (blockId == null || blockIndex.containsKey(blockId)) {
                return;
            }

            CharacterId safeParentId = (parentId == null) ? ROOT_BLOCK_ID : parentId;
            BlockNode parent = blockIndex.getOrDefault(safeParentId, root);

            BlockNode newBlock = new BlockNode(blockId, safeParentId);
            parent.addChild(newBlock);
            blockIndex.put(blockId, newBlock);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public CRDTOperation deleteBlock(CharacterId blockId, int siteId) {
        rwLock.writeLock().lock();
        try {
            if (blockId == null || ROOT_BLOCK_ID.equals(blockId)) {
                return null;
            }

            BlockNode block = blockIndex.get(blockId);
            if (block == null || block.isDeleted()) {
                return null;
            }

            block.setDeleted(true);
            return CRDTOperation.deleteBlock(siteId, blockId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void applyRemoteDeleteBlock(CRDTOperation op) {
        if (op == null || op.getType() != CRDTOperation.Type.DELETE_BLOCK) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            CharacterId blockId = op.getBlockId();
            if (blockId == null || ROOT_BLOCK_ID.equals(blockId)) {
                return;
            }

            BlockNode block = blockIndex.get(blockId);
            if (block != null) {
                block.setDeleted(true);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<BlockNode> getVisibleBlocks() {
        rwLock.readLock().lock();
        try {
            List<BlockNode> result = new ArrayList<>();
            dfsCollectVisibleBlocks(root, result);
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<CharacterId> getVisibleBlockIds() {
        rwLock.readLock().lock();
        try {
            List<CharacterId> ids = new ArrayList<>();
            List<BlockNode> visibleBlocks = new ArrayList<>();
            dfsCollectVisibleBlocks(root, visibleBlocks);

            for (BlockNode block : visibleBlocks) {
                ids.add(block.getBlockId());
            }

            return ids;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getVisibleDocumentText() {
        rwLock.readLock().lock();
        try {
            List<BlockNode> visibleBlocks = new ArrayList<>();
            dfsCollectVisibleBlocks(root, visibleBlocks);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < visibleBlocks.size(); i++) {
                sb.append(visibleBlocks.get(i).getContent().getVisibleText());
                if (i < visibleBlocks.size() - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public BlockNode getBlock(CharacterId blockId) {
        rwLock.readLock().lock();
        try {
            return blockIndex.get(blockId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean exists(CharacterId blockId) {
        rwLock.readLock().lock();
        try {
            return blockIndex.containsKey(blockId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int visibleBlockCount() {
        rwLock.readLock().lock();
        try {
            int[] count = {0};
            dfsCountVisibleBlocks(root, count);
            return count[0];
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void dfsCollectVisibleBlocks(BlockNode node, List<BlockNode> out) {
        if (node != root && !node.isDeleted()) {
            out.add(node);
        }

        for (BlockNode child : node.getChildrenSnapshot()) {
            dfsCollectVisibleBlocks(child, out);
        }
    }

    private void dfsCountVisibleBlocks(BlockNode node, int[] count) {
        if (node != root && !node.isDeleted()) {
            count[0]++;
        }

        for (BlockNode child : node.getChildrenSnapshot()) {
            dfsCountVisibleBlocks(child, count);
        }
    }
}
