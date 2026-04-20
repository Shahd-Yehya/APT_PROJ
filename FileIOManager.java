import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports and exports text files.
 */
public class FileIOManager {
    private final BlockCRDT blockCRDT;
    private final int siteId;
    private int clock;

    public FileIOManager(BlockCRDT blockCRDT, int siteId, int clock) {
        this.blockCRDT = blockCRDT;
        this.siteId = siteId;
        this.clock = clock;
    }

    /**
     * Imports a plain .txt file and creates blocks/characters in the CRDT structure.
     * Each line becomes a separate block.
     * 
     * @param filePath Path to the .txt file to import
     * @return true if import successful, false otherwise
     */
    public boolean importTextFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            CharacterId currentParentBlock = null; // null means root

            while ((line = reader.readLine()) != null) {
                // Create a new block for each line
                clock++;
                CRDTOperation blockOp = blockCRDT.insertBlock(siteId, clock, currentParentBlock);
                
                if (blockOp == null) {
                    return false;
                }

                CharacterId blockId = blockOp.getBlockId();
                BlockNode block = blockCRDT.getBlock(blockId);

                if (block == null) {
                    return false;
                }

                // Insert characters from the line into the block
                CharacterCRDT content = block.getContent();
                CharacterId parentId = CharacterCRDT.ROOT_ID;

                for (char c : line.toCharArray()) {
                    clock++;
                    content.insert(siteId, clock, c, parentId, blockId, false, false);
                    parentId = new CharacterId(siteId, clock);
                }

                // Next block becomes child of current block (creating hierarchy)
                currentParentBlock = blockId;
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error importing file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports the entire document to a plain .txt file.
     * Preserves line breaks (each visible block is a line).
     * For now, formatting is not preserved in basic .txt export.
     * 
     * @param filePath Path where the .txt file should be saved
     * @return true if export successful, false otherwise
     */
    public boolean exportTextFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            List<BlockNode> visibleBlocks = blockCRDT.getVisibleBlocks();

            for (int i = 0; i < visibleBlocks.size(); i++) {
                BlockNode block = visibleBlocks.get(i);
                String blockText = block.getContent().getVisibleText();
                
                writer.write(blockText);
                
                // Add newline after each block except the last
                if (i < visibleBlocks.size() - 1) {
                    writer.newLine();
                }
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error exporting file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports document with formatting metadata.
     * Creates a format that includes bold/italic information.
     * Format: Each character line contains: char|bold|italic
     * Blocks separated by "---BLOCK---"
     * 
     * @param filePath Path where the formatted file should be saved
     * @return true if export successful, false otherwise
     */
    public boolean exportFormattedFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            List<BlockNode> visibleBlocks = blockCRDT.getVisibleBlocks();

            for (int blockIdx = 0; blockIdx < visibleBlocks.size(); blockIdx++) {
                BlockNode block = visibleBlocks.get(blockIdx);
                List<CharacterNode> visibleChars = block.getContent().getVisibleNodes();

                for (CharacterNode charNode : visibleChars) {
                    // Format: character|bold|italic
                    writer.write(charNode.getValue());
                    writer.write("|");
                    writer.write(String.valueOf(charNode.isBold()));
                    writer.write("|");
                    writer.write(String.valueOf(charNode.isItalic()));
                    writer.newLine();
                }

                // Block separator
                if (blockIdx < visibleBlocks.size() - 1) {
                    writer.write("---BLOCK---");
                    writer.newLine();
                }
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error exporting formatted file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Imports a formatted file with formatting metadata.
     * 
     * @param filePath Path to the formatted file
     * @return true if import successful, false otherwise
     */
    public boolean importFormattedFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            CharacterId currentParentBlock = null;
            List<String> currentBlockLines = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (line.equals("---BLOCK---")) {
                    // Process accumulated block
                    if (!currentBlockLines.isEmpty()) {
                        createBlockFromFormattedLines(currentBlockLines, currentParentBlock);
                        currentBlockLines.clear();
                    }
                    // The block we just created becomes parent for next block
                    List<BlockNode> visibleBlocks = blockCRDT.getVisibleBlocks();
                    if (!visibleBlocks.isEmpty()) {
                        currentParentBlock = visibleBlocks.get(visibleBlocks.size() - 1).getBlockId();
                    }
                } else {
                    currentBlockLines.add(line);
                }
            }

            // Process last block if any
            if (!currentBlockLines.isEmpty()) {
                createBlockFromFormattedLines(currentBlockLines, currentParentBlock);
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error importing formatted file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to create a block from formatted lines.
     * Each line format: character|bold|italic
     */
    private void createBlockFromFormattedLines(List<String> lines, CharacterId parentBlock) {
        clock++;
        CRDTOperation blockOp = blockCRDT.insertBlock(siteId, clock, parentBlock);
        
        if (blockOp == null) return;

        CharacterId blockId = blockOp.getBlockId();
        BlockNode block = blockCRDT.getBlock(blockId);

        if (block == null) return;

        CharacterCRDT content = block.getContent();
        CharacterId charParentId = CharacterCRDT.ROOT_ID;

        for (String line : lines) {
            String[] parts = line.split("\\|");
            if (parts.length == 3) {
                char charValue = parts[0].isEmpty() ? ' ' : parts[0].charAt(0);
                boolean bold = Boolean.parseBoolean(parts[1]);
                boolean italic = Boolean.parseBoolean(parts[2]);

                clock++;
                content.insert(siteId, clock, charValue, charParentId, blockId, bold, italic);
                charParentId = new CharacterId(siteId, clock);
            }
        }
    }

    /**
     * Gets the current clock value.
     */
    public int getClock() {
        return clock;
    }

    /**
     * Updates the clock value.
     */
    public void setClock(int newClock) {
        this.clock = newClock;
    }
}
