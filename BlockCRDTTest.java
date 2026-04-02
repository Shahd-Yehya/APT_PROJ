import java.util.List;

public class BlockCRDTTest {

    public static void main(String[] args) {
        testInsertBlocksInSequence();
        testDeleteBlockWithTombstone();
        testConcurrentInsertDeterministicOrder();
        testDuplicateInsertIgnored();
        testDeleteNonExistingBlock();
        testBlockTextIntegration();

        System.out.println();
        System.out.println("All BlockCRDT tests passed successfully.");
    }

    private static void testInsertBlocksInSequence() {
        BlockCRDT crdt = new BlockCRDT();

        CRDTOperation opA = crdt.insertBlock(1, 1, BlockCRDT.ROOT_BLOCK_ID);
        assertNotNull(opA, "opA should not be null");

        CRDTOperation opB = crdt.insertBlock(1, 2, opA.getBlockId());
        assertNotNull(opB, "opB should not be null");

        CRDTOperation opC = crdt.insertBlock(1, 3, opB.getBlockId());
        assertNotNull(opC, "opC should not be null");

        List<CharacterId> ids = crdt.getVisibleBlockIds();

        assertEquals(3, ids.size(), "Visible block count should be 3");
        assertEquals(opA.getBlockId(), ids.get(0), "First block should be A");
        assertEquals(opB.getBlockId(), ids.get(1), "Second block should be B");
        assertEquals(opC.getBlockId(), ids.get(2), "Third block should be C");

        System.out.println("testInsertBlocksInSequence PASSED");
    }

    private static void testDeleteBlockWithTombstone() {
        BlockCRDT crdt = new BlockCRDT();

        CRDTOperation opA = crdt.insertBlock(1, 1, BlockCRDT.ROOT_BLOCK_ID);
        CRDTOperation opB = crdt.insertBlock(1, 2, opA.getBlockId());
        CRDTOperation opC = crdt.insertBlock(1, 3, opB.getBlockId());

        CRDTOperation deleteOp = crdt.deleteBlock(opB.getBlockId(), 99);
        assertNotNull(deleteOp, "Delete operation should not be null");

        List<CharacterId> ids = crdt.getVisibleBlockIds();

        assertEquals(2, ids.size(), "Visible block count should be 2 after deleting B");
        assertEquals(opA.getBlockId(), ids.get(0), "First visible block should be A");
        assertEquals(opC.getBlockId(), ids.get(1), "Second visible block should be C");

        BlockNode deletedBlock = crdt.getBlock(opB.getBlockId());
        assertNotNull(deletedBlock, "Deleted block must still exist in index");
        assertTrue(deletedBlock.isDeleted(), "Deleted block must be tombstoned, not removed");

        System.out.println("testDeleteBlockWithTombstone PASSED");
    }

    private static void testConcurrentInsertDeterministicOrder() {
        BlockCRDT crdt = new BlockCRDT();

        CRDTOperation op1 = crdt.insertBlock(1, 10, BlockCRDT.ROOT_BLOCK_ID);
        CRDTOperation op2 = crdt.insertBlock(2, 10, BlockCRDT.ROOT_BLOCK_ID);

        assertNotNull(op1, "op1 should not be null");
        assertNotNull(op2, "op2 should not be null");

        List<CharacterId> firstRead = crdt.getVisibleBlockIds();
        List<CharacterId> secondRead = crdt.getVisibleBlockIds();

        assertEquals(2, firstRead.size(), "There should be 2 visible blocks");
        assertEquals(firstRead.get(0), secondRead.get(0), "Order must stay stable");
        assertEquals(firstRead.get(1), secondRead.get(1), "Order must stay stable");

        CharacterId expectedFirst;
        CharacterId expectedSecond;

        if (op1.getBlockId().getClock() > op2.getBlockId().getClock()) {
            expectedFirst = op1.getBlockId();
            expectedSecond = op2.getBlockId();
        } else if (op1.getBlockId().getClock() < op2.getBlockId().getClock()) {
            expectedFirst = op2.getBlockId();
            expectedSecond = op1.getBlockId();
        } else {
            if (op1.getBlockId().getSiteId() < op2.getBlockId().getSiteId()) {
                expectedFirst = op1.getBlockId();
                expectedSecond = op2.getBlockId();
            } else {
                expectedFirst = op2.getBlockId();
                expectedSecond = op1.getBlockId();
            }
        }

        assertEquals(expectedFirst, firstRead.get(0), "First block order is wrong");
        assertEquals(expectedSecond, firstRead.get(1), "Second block order is wrong");

        System.out.println("testConcurrentInsertDeterministicOrder PASSED");
    }

    private static void testDuplicateInsertIgnored() {
        BlockCRDT crdt = new BlockCRDT();

        CRDTOperation op1 = crdt.insertBlock(5, 5, BlockCRDT.ROOT_BLOCK_ID);
        assertNotNull(op1, "First insert must succeed");

        CRDTOperation op2 = crdt.insertBlock(5, 5, BlockCRDT.ROOT_BLOCK_ID);
        assertNull(op2, "Duplicate insert with same CharacterId must be ignored");

        assertEquals(1, crdt.visibleBlockCount(), "Only one visible block should exist");

        System.out.println("testDuplicateInsertIgnored PASSED");
    }

    private static void testDeleteNonExistingBlock() {
        BlockCRDT crdt = new BlockCRDT();

        CharacterId fakeId = new CharacterId(99, 99);
        CRDTOperation deleteOp = crdt.deleteBlock(fakeId, 1);

        assertNull(deleteOp, "Deleting a non-existing block should return null");
        assertEquals(0, crdt.visibleBlockCount(), "Visible block count should remain 0");

        System.out.println("testDeleteNonExistingBlock PASSED");
    }

    private static void testBlockTextIntegration() {
        BlockCRDT crdt = new BlockCRDT();

        CRDTOperation opA = crdt.insertBlock(1, 1, BlockCRDT.ROOT_BLOCK_ID);
        CRDTOperation opB = crdt.insertBlock(1, 2, opA.getBlockId());

        BlockNode blockA = crdt.getBlock(opA.getBlockId());
        BlockNode blockB = crdt.getBlock(opB.getBlockId());

        assertNotNull(blockA, "blockA should exist");
        assertNotNull(blockB, "blockB should exist");

        CharacterCRDT charA = blockA.getContent();
        CharacterCRDT charB = blockB.getContent();

        CharacterId parentA = CharacterCRDT.ROOT_ID;
        charA.insert(1, 1, 'H', parentA, opA.getBlockId(), false, false);
        parentA = new CharacterId(1, 1);
        charA.insert(1, 2, 'i', parentA, opA.getBlockId(), false, false);

        CharacterId parentB = CharacterCRDT.ROOT_ID;
        charB.insert(2, 1, 'B', parentB, opB.getBlockId(), false, false);
        parentB = new CharacterId(2, 1);
        charB.insert(2, 2, 'y', parentB, opB.getBlockId(), false, false);
        parentB = new CharacterId(2, 2);
        charB.insert(2, 3, 'e', parentB, opB.getBlockId(), false, false);

        String fullText = crdt.getVisibleDocumentText();
        assertEquals("Hi\nBye", fullText, "Document text should join visible block texts with newline");

        System.out.println("testBlockTextIntegration PASSED");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError(message + " | Expected null but got: " + obj);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) {
            return;
        }

        if (expected != null && expected.equals(actual)) {
            return;
        }

        throw new AssertionError(message + " | Expected: " + expected + " , Actual: " + actual);
    }
}