package test;
import model.*;
import repository.*;
import service.*;
import ui.*;

public class DocumentTest {

    public static void main(String[] args) {
        test1_insertBlockAndGetFullText();
        test2_normalSplit();
        // edge cases for split
        test3_splitAtStart();
        test4_splitAtEnd();
        System.out.println("All tests done.");
    }

    // Test 1 - insert one block with text, getFullText returns it correctly
    static void test1_insertBlockAndGetFullText() {
        BlockCRDT blockCRDT = new BlockCRDT();
        Document doc = new Document(blockCRDT);
        int siteId = 1;
        int clock = 0;

        // insert a block after root
        clock++;
        CRDTOperation op = blockCRDT.insertBlock(siteId, clock, null);
        CharacterId blockId = op.getBlockId();
        BlockNode block = blockCRDT.getBlock(blockId);

        // insert "Shahd" to the block manually
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        clock++;
        block.getContent().insert(siteId, clock, 'S', parentId, blockId, false, false);
        parentId = new CharacterId(siteId, clock);
        clock++;
        block.getContent().insert(siteId, clock, 'h', parentId, blockId, false, false);
        parentId = new CharacterId(siteId, clock);
        clock++;
        block.getContent().insert(siteId, clock, 'a', parentId, blockId, false, false);
        parentId = new CharacterId(siteId, clock);
        clock++;
        block.getContent().insert(siteId, clock, 'h', parentId, blockId, false, false);
        parentId = new CharacterId(siteId, clock);
        clock++;
        block.getContent().insert(siteId, clock, 'd', parentId, blockId, false, false);

        // check
        String result = doc.getFullText();
        if (result.equals("Shahd")) {
            System.out.println("Test 1 PASSED — getFullText: \"" + result + "\"");
        } else {
            //debugging
            System.out.println("Test 1 FAILED — expected \"Shahd\" but got \"" + result + "\"");
        }
    }

    // Test 2 - normal split in the middle
    static void test2_normalSplit() {
        BlockCRDT blockCRDT = new BlockCRDT();
        Document doc = new Document(blockCRDT);
        int siteId = 1;
        int clock = 0;

        // insert block with "Shahd Yehya"
        clock++;
        CRDTOperation op = blockCRDT.insertBlock(siteId, clock, null);
        CharacterId blockId = op.getBlockId();
        BlockNode block = blockCRDT.getBlock(blockId);

        String text = "Shahd Yehya";
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        for (char c : text.toCharArray()) {
            clock++;
            block.getContent().insert(siteId, clock, c, parentId, blockId, false, false);
            parentId = new CharacterId(siteId, clock);
        }

        // split at position 5 -> "Shahd" and " Yehya"
        SplitBlockOperation splitter = new SplitBlockOperation(blockCRDT, siteId, clock);
        splitter.splitBlock(blockId, 5);

        String result = doc.getFullText();
        if (result.equals("Shahd\n Yehya")) {
            System.out.println("Test 2 PASSED — getFullText: \"" + result + "\"");
        } else {
            System.out.println("Test 2 FAILED — expected \"Shahd\\n Yehya\" but got \"" + result + "\"");
        }
    }

    // Test 3 - split at position 0
    static void test3_splitAtStart() {
        BlockCRDT blockCRDT = new BlockCRDT();
        Document doc = new Document(blockCRDT);
        int siteId = 1;
        int clock = 0;

        clock++;
        CRDTOperation op = blockCRDT.insertBlock(siteId, clock, null);
        CharacterId blockId = op.getBlockId();
        BlockNode block = blockCRDT.getBlock(blockId);

        String text = "Shahd";
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        for (char c : text.toCharArray()) {
            clock++;
            block.getContent().insert(siteId, clock, c, parentId, blockId, false, false);
            parentId = new CharacterId(siteId, clock);
        }

        // split at 0 -> original block become empty, new block gets "Shahd"
        SplitBlockOperation splitter = new SplitBlockOperation(blockCRDT, siteId, clock);
        splitter.splitBlock(blockId, 0);

        String result = doc.getFullText();
        if (result.equals("\nShahd")) {
            System.out.println("Test 3 PASSED — getFullText: \"" + result + "\"");
        } else {
            System.out.println("Test 3 FAILED — expected \"\\nShahd\" but got \"" + result + "\"");
        }
    }

    // Test 4 - split at the end
    static void test4_splitAtEnd() {
        BlockCRDT blockCRDT = new BlockCRDT();
        Document doc = new Document(blockCRDT);
        int siteId = 1;
        int clock = 0;

        clock++;
        CRDTOperation op = blockCRDT.insertBlock(siteId, clock, null);
        CharacterId blockId = op.getBlockId();
        BlockNode block = blockCRDT.getBlock(blockId);

        String text = "Shahd";
        CharacterId parentId = CharacterCRDT.ROOT_ID;
        for (char c : text.toCharArray()) {
            clock++;
            block.getContent().insert(siteId, clock, c, parentId, blockId, false, false);
            parentId = new CharacterId(siteId, clock);
        }

        // split at end -> original keeps "Shahd", new block is empty
        SplitBlockOperation splitter = new SplitBlockOperation(blockCRDT, siteId, clock);
        splitter.splitBlock(blockId, text.length());

        String result = doc.getFullText();
        if (result.equals("Shahd\n")) {
            System.out.println("Test 4 PASSED — getFullText: \"" + result + "\"");
        } else {
            System.out.println("Test 4 FAILED — expected \"Shahd\\n\" but got \"" + result + "\"");
        }
    }
}
