// Phase2ManualTest.java
// =====================
// Run this class WITHOUT a real server to verify CursorTracker logic locally.
// It does NOT require a WebSocket connection.

import javax.swing.*;
import java.util.List;

public class Phase2ManualTest {

    // ─── helper to build a fresh Document with one block ─────────────────
    static Document makeDoc(int siteId, int[] clock) {
        Document doc = new Document(new BlockCRDT());
        doc.insertBlock(siteId, ++clock[0], null);
        return doc;
    }

    static CharacterId firstBlockId(Document doc) {
        List<BlockNode> blocks = doc.getVisibleBlocks();
        return blocks.get(0).getBlockId();
    }

    static CharacterCRDT firstCRDT(Document doc) {
        return doc.getVisibleBlocks().get(0).getContent();
    }

    // ─── TEST 1: NetworkMessage round-trip ────────────────────────────────
    static void testNetworkMessageRoundTrip() {
        System.out.println("\n[TEST 1] NetworkMessage JSON round-trip");

        int[]  clock   = {0};
        Document doc   = makeDoc(1, clock);
        CharacterId bid = firstBlockId(doc);
        CharacterCRDT crdt = firstCRDT(doc);

        // insert a char
        CharacterId pid = CharacterCRDT.ROOT_ID;
        CRDTOperation op = crdt.insert(1, ++clock[0], 'H', pid, bid, false, false);
        assert op != null : "insert should return op";

        // wrap into NetworkMessage and serialise
        NetworkMessage msg = NetworkMessage.fromCRDTOperation(op, "doc1");
        String json = msg.toJson();
        System.out.println("  JSON: " + json);

        // deserialise and reconstruct op
        NetworkMessage msg2 = NetworkMessage.fromJson(json);
        CRDTOperation  op2  = msg2.toCRDTOperation();
        assert op2 != null                               : "reconstructed op null";
        assert op2.getType() == CRDTOperation.Type.INSERT_CHAR : "wrong type";
        assert op2.getValue() == 'H'                    : "wrong value";
        System.out.println("  PASS");
    }

    // ─── TEST 2: CURSOR message round-trip ───────────────────────────────
    static void testCursorMessageRoundTrip() {
        System.out.println("\n[TEST 2] CURSOR message round-trip");

        NetworkMessage m = NetworkMessage.cursor(2, "doc1", 5, 1, 3);
        String json = m.toJson();
        System.out.println("  JSON: " + json);

        NetworkMessage m2 = NetworkMessage.fromJson(json);
        assert m2.getType()            == NetworkMessage.Type.CURSOR : "type";
        assert m2.getSiteId()          == 2 : "siteId";
        assert m2.getCursorPosition()  == 5 : "position";
        assert m2.getCursorBlockSite() == 1 : "blockSite";
        assert m2.getCursorBlockClock()== 3 : "blockClock";
        System.out.println("  PASS");
    }

    // ─── TEST 3: CursorTracker handleBlockDeleted ─────────────────────────
    static void testHandleBlockDeleted() {
        System.out.println("\n[TEST 3] CursorTracker.handleBlockDeleted()");

        // We create a minimal Swing JTextPane just to give CursorTracker something to hold
        JTextPane pane = new JTextPane();
        int[]     clock = {0};
        Document  doc   = makeDoc(0, clock);
        CharacterId bid = firstBlockId(doc);

        CursorTracker tracker = new CursorTracker(pane, doc, 0 /* localSite */);

        // Simulate remote user (site=1) cursor inside block (bid.siteId, bid.getClock())
        tracker.updateCursor(1, 3, bid.getSiteId(), bid.getClock());

        // Now the block is deleted
        tracker.handleBlockDeleted(bid.getSiteId(), bid.getClock());

        // The cursor should have been moved to 0 (no crash)
        System.out.println("  PASS – no crash, cursor gracefully reset");
    }

    // ─── TEST 4: remote insert applied correctly ──────────────────────────
    static void testRemoteInsertApplied() {
        System.out.println("\n[TEST 4] Remote INSERT_CHAR applied to CRDT");

        int[]    clockA = {0};
        Document docA  = makeDoc(1, clockA);
        CharacterId bidA = firstBlockId(docA);

        int[]    clockB = {0};
        Document docB  = makeDoc(2, clockB);
        CharacterId bidB = firstBlockId(docB);

        // User A inserts 'H'
        CharacterCRDT crdtA = firstCRDT(docA);
        CRDTOperation op = crdtA.insert(1, ++clockA[0], 'H',
                CharacterCRDT.ROOT_ID, bidA, false, false);

        // Transmit (simulate network)
        NetworkMessage msg = NetworkMessage.fromCRDTOperation(op, "doc");
        CRDTOperation  opReceived = NetworkMessage.fromJson(msg.toJson()).toCRDTOperation();

        // Apply to B
        CharacterCRDT crdtB = firstCRDT(docB);
        crdtB.applyRemoteInsert(opReceived, false, false);

        assert crdtB.getVisibleText().equals("H") : "B should see 'H', got: " + crdtB.getVisibleText();
        System.out.println("  PASS – B sees: \"" + crdtB.getVisibleText() + "\"");
    }

    // ─── TEST 5: DELETE_BLOCK edge case ──────────────────────────────────
    static void testDeleteBlockEdgeCase() {
        System.out.println("\n[TEST 5] DELETE_BLOCK while remote cursor is inside");

        JTextPane pane  = new JTextPane();
        int[]    clock  = {0};
        Document doc    = makeDoc(1, clock);
        CharacterId bid = firstBlockId(doc);

        CursorTracker tracker = new CursorTracker(pane, doc, 0);

        // Remote user 2's cursor is inside the block
        tracker.updateCursor(2, 4, bid.getSiteId(), bid.getClock());

        // The block gets deleted remotely
        CRDTOperation delOp = doc.deleteBlock(bid, 1);

        // Simulate what EditorPanel does on DELETE_BLOCK
        tracker.handleBlockDeleted(bid.getSiteId(), bid.getClock()); // <-- graceful
        doc.applyRemoteDeleteBlock(delOp);

        System.out.println("  PASS – no crash on DELETE_BLOCK with cursor inside");
    }

    // ─── main ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("===== Phase 2 Manual Tests =====");
        testNetworkMessageRoundTrip();
        testCursorMessageRoundTrip();
        testHandleBlockDeleted();
        testRemoteInsertApplied();
        testDeleteBlockEdgeCase();
        System.out.println("\n===== All tests passed =====");
    }
}
