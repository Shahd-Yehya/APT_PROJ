package src;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class EditorUI {
    // UI components
    private JFrame mainWindow;       // main window
    private JTextPane textPane;      // text area
    private JPanel usersPanel;       // panel 3la el gamb 3shan el active users
    private JLabel statusLabel;      // bottom label 3shan el session ID

    // CRDT and session state
    private BlockCRDT blockCRDT;     // block CRDT (Phase 1)
    private String sessionId;        // session id
    private int siteId;              // unique site ID for the user
    private int clock;               // local clock 
    private CharacterId currentBlockId; // current block    
    private boolean isBold = false;      // bold toggle
    private boolean isItalic = false;    // italic toggle

    // track el active users for the users panel
    private final List<Integer> activeUsers = new ArrayList<>();

    // colors for the cursors
    public static final Color[] USER_COLORS = {
        Color.BLUE, Color.RED, new Color(0, 150, 0), Color.ORANGE
    };

    // Constructor
    public EditorUI(int siteId) {
        this.siteId = siteId;
        this.clock = 0;
        this.blockCRDT = new BlockCRDT();

        // hncreate awel block automatic 3shan el user yktb feh
        clock++;
        CRDTOperation firstBlockOp = blockCRDT.insertBlock(siteId, clock, null);
        this.currentBlockId = firstBlockOp.getBlockId();

        buildUI();
    }

    // function elly btbuild el UI 
    private void buildUI() {
        // Main window
        mainWindow = new JFrame("Collaborative Editor — Site " + siteId);
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setSize(900, 600);
        mainWindow.setLayout(new BorderLayout());

        // Toolbar (top)
        JToolBar toolbar = buildToolbar();
        mainWindow.add(toolbar, BorderLayout.NORTH);

        // Text area (center)
        textPane = new JTextPane();
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        // hnattach el keyboard listener 3shan ncapture kol 7arf bytktb
        textPane.addKeyListener(buildKeyListener());
        JScrollPane scrollPane = new JScrollPane(textPane);
        mainWindow.add(scrollPane, BorderLayout.CENTER);

        // Active users panel (right side)
        usersPanel = new JPanel();
        usersPanel.setLayout(new BoxLayout(usersPanel, BoxLayout.Y_AXIS));
        usersPanel.setPreferredSize(new Dimension(150, 0));
        usersPanel.setBorder(BorderFactory.createTitledBorder("Active Users"));
        mainWindow.add(usersPanel, BorderLayout.EAST);

        // Status bar (bottom)
        statusLabel = new JLabel("  Not connected to any session");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        mainWindow.add(statusLabel, BorderLayout.SOUTH);

        // show el window f nos el shasha
        mainWindow.setLocationRelativeTo(null);
        mainWindow.setVisible(true);
    }

    // Toolbar - Bold, Italic, Join Session
    // hnbuild el toolbar elly feh Bold w Italic w Join
    private JToolBar buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false); 

        // button el bold
        JButton boldBtn = new JButton("BOLD"); 
        boldBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        boldBtn.setToolTipText("Bold");
        boldBtn.addActionListener(e -> onBoldClicked());
        toolbar.add(boldBtn);

        // button el italic
        JButton italicBtn = new JButton("ITALIC"); 
        italicBtn.setFont(new Font("SansSerif", Font.ITALIC, 14));
        italicBtn.setToolTipText("Italic");
        italicBtn.addActionListener(e -> onItalicClicked());
        toolbar.add(italicBtn);

        toolbar.addSeparator();

        // button el Join Session 3shan nshof el dialog
        JButton joinBtn = new JButton("Join Session");
        joinBtn.addActionListener(e -> showJoinSessionDialog());
        toolbar.add(joinBtn);

        return toolbar;
    }

    // keyboard listener 3shan n-capture kol key 
    private KeyListener buildKeyListener() {
        return new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent e) {
                char typed = e.getKeyChar();

                // hnignore el control characters 3shan hnt3aml m3 el Enter w el Backspace lwa7dhom
                if (typed == KeyEvent.CHAR_UNDEFINED) return;
                if (typed == '\b') return; // Backspace hnt3aml m3ah f keyPressed
                if (typed < 32) return;    

                // hninsert el char fe el CRDT 
                BlockNode currentBlock = blockCRDT.getBlock(currentBlockId);
                if (currentBlock == null || currentBlock.isDeleted()) return;

                // hnshof el parentID 3la 7asab el cursor position
                int cursorPos = textPane.getCaretPosition();
                
                // Find target block and relative position
                BlockNode targetBlock = null;
                int relativePos = cursorPos;
                java.util.List<BlockNode> blocks = blockCRDT.getVisibleBlocks();
                for (BlockNode b : blocks) {
                    int len = b.getContent().getVisibleText().length();
                    if (relativePos <= len) {
                        targetBlock = b;
                        break;
                    }
                    relativePos -= (len + 1); // +1 flag for \n
                }
                
                if (targetBlock == null) {
                    if (!blocks.isEmpty()) {
                        targetBlock = blocks.get(blocks.size() - 1);
                        relativePos = targetBlock.getContent().getVisibleText().length();
                    } else return;
                }
                
                currentBlockId = targetBlock.getBlockId();
                CharacterId parentId = targetBlock.getContent().getParentIdForCursor(relativePos);

                clock++;
                CRDTOperation op = targetBlock.getContent().insert(
                    siteId, clock, typed, parentId, currentBlockId, isBold, isItalic
                );

                // hnrefresh el text pane 3shan nshow el taghyer
                refreshTextPane(1);

                // TODO (for kareem): send op over the network
                // collaborationClient.sendOperation(op);

                // hnconsume el event 3shan Swing msh hyinsert el char lwa7do
                e.consume();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    handleBackspace();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleEnter();
                    e.consume();
                }
            }
        };
    }

    // hnt3aml m3 el Enter key 3shan nsplit el block
    private void handleEnter() {
        int cursorPos = textPane.getCaretPosition();
        
        // Find target block and relative position
        BlockNode targetBlock = null;
        int relativePos = cursorPos;
        java.util.List<BlockNode> blocks = blockCRDT.getVisibleBlocks();
        for (BlockNode b : blocks) {
            int len = b.getContent().getVisibleText().length();
            if (relativePos <= len) {
                targetBlock = b;
                break;
            }
            relativePos -= (len + 1);
        }
        
        if (targetBlock == null) {
            if (!blocks.isEmpty()) {
                targetBlock = blocks.get(blocks.size() - 1);
                relativePos = targetBlock.getContent().getVisibleText().length();
            } else return;
        }

        currentBlockId = targetBlock.getBlockId();
        
        // hnst3ml el split block logic elly f SplitBlockOperation
        SplitBlockOperation splitter = new SplitBlockOperation(blockCRDT, siteId, clock);
        splitter.splitBlock(currentBlockId, relativePos);
        
        clock++; 
        
        // hnshof el block el gded elly et3ml 3shan n7rk el cursor feh
        List<BlockNode> visible = blockCRDT.getVisibleBlocks();
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).getBlockId().equals(currentBlockId) && i + 1 < visible.size()) {
                currentBlockId = visible.get(i + 1).getBlockId();
                break;
            }
        }

        refreshTextPane(1); // hnmove ll satr el gded
    }

    // Handle backspace to delete the character before the cursor
    private void handleBackspace() {
        int cursorPos = textPane.getCaretPosition();
        if (cursorPos <= 0) return; // mafesh 7aga ttdelete

        // Find target block and relative position
        BlockNode targetBlock = null;
        BlockNode prevBlock = null;
        int relativePos = cursorPos;
        java.util.List<BlockNode> blocks = blockCRDT.getVisibleBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            BlockNode b = blocks.get(i);
            int len = b.getContent().getVisibleText().length();
            if (relativePos <= len) {
                targetBlock = b;
                if (i > 0) prevBlock = blocks.get(i - 1);
                break;
            }
            relativePos -= (len + 1);
        }

        if (targetBlock == null) return;
        
        currentBlockId = targetBlock.getBlockId();

        if (relativePos > 0) {
            // hnshof el visible char elly abl el cursor 3latol
            java.util.List<CharacterNode> visible = targetBlock.getContent().getVisibleNodes();
            if (visible.isEmpty() || relativePos > visible.size()) return;

            CharacterNode toDelete = visible.get(relativePos - 1);
            clock++;
            targetBlock.getContent().delete(
                toDelete.getId(), currentBlockId, siteId
            );
            refreshTextPane(-1);
        } else if (prevBlock != null) {
            // We are at start of block, so we mergeel block dah m3 el previous
            java.util.List<CharacterNode> nodesToMove = targetBlock.getContent().getVisibleNodes();
            java.util.List<CharacterNode> prevNodes = prevBlock.getContent().getVisibleNodes();
            
            CharacterId parentId = prevNodes.isEmpty() ? CharacterCRDT.ROOT_ID : prevNodes.get(prevNodes.size()-1).getId();
            
            for (CharacterNode node : nodesToMove) {
                targetBlock.getContent().delete(node.getId(), targetBlock.getBlockId(), siteId);
                clock++;
                prevBlock.getContent().insert(siteId, clock, node.getValue(), parentId, prevBlock.getBlockId(), node.isBold(), node.isItalic());
                parentId = new CharacterId(siteId, clock);
            }
            
            // delete empty block
            blockCRDT.deleteBlock(targetBlock.getBlockId(), siteId);
            currentBlockId = prevBlock.getBlockId();
            refreshTextPane(-1);
        }

        // TODO (for kareem): send op over the network
        // collaborationClient.sendOperation(op);
    }

    // de el function elly btrefresh el text pane mn el CRDT state
    public void refreshTextPane(int caretAdjustment) {
        // hnupdate el UI mn el CRDT bs w lazem f el Swing thread
        SwingUtilities.invokeLater(() -> {
            int caretPos = textPane.getCaretPosition();
            StyledDocument doc = textPane.getStyledDocument();

            try {
                doc.remove(0, doc.getLength()); 

                java.util.List<BlockNode> blocks = blockCRDT.getVisibleBlocks();
                for (int i = 0; i < blocks.size(); i++) {
                    java.util.List<CharacterNode> nodes = blocks.get(i).getContent().getVisibleNodes();
                    for (CharacterNode node : nodes) {
                        SimpleAttributeSet set = new SimpleAttributeSet();
                        StyleConstants.setBold(set, node.isBold());
                        StyleConstants.setItalic(set, node.isItalic());
                        doc.insertString(doc.getLength(), String.valueOf(node.getValue()), set);
                    }
                    if (i < blocks.size() - 1) {
                        doc.insertString(doc.getLength(), "\n", null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // hnshof el new safe position
            int newTextLen = doc.getLength();
            int newPos = caretPos + caretAdjustment;
            int safePos = Math.max(0, Math.min(newPos, newTextLen));
            textPane.setCaretPosition(safePos);
        });
    }

    // dialog bta3et el Join Session 3shan n-enter el ID
    private void showJoinSessionDialog() {
        String input = JOptionPane.showInputDialog(
            mainWindow,
            "Enter Session ID to join:",
            "Join Collaboration Session",
            JOptionPane.PLAIN_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) return;

        sessionId = input.trim();
        statusLabel.setText("  Connected to session: " + sessionId);

        // TODO (for kareem): connect to the WebSocket server with this sessionId
        // collaborationClient.connect(sessionId);

        JOptionPane.showMessageDialog(mainWindow,
            "Joined session: " + sessionId,
            "Connected",
            JOptionPane.INFORMATION_MESSAGE);
    }

    // users panel 3shan n3rf men elly m3ana f el session
    public void updateActiveUsers(List<Integer> userSiteIds) {
        SwingUtilities.invokeLater(() -> {
            usersPanel.removeAll();

            for (int i = 0; i < userSiteIds.size(); i++) {
                int id = userSiteIds.get(i);
                Color color = USER_COLORS[i % USER_COLORS.length];

                JLabel userLabel = new JLabel("  ● User " + id);
                userLabel.setForeground(color);
                userLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
                usersPanel.add(userLabel);
                usersPanel.add(Box.createVerticalStrut(6)); 
            }

            usersPanel.revalidate();
            usersPanel.repaint();
        });
    }

    // 3shan napply ay remote operation gaya mn el network (TODO for kareem)
    public void applyRemoteOperation(CRDTOperation op) {
        // hncheck el operation type w nshof hn3ml eh
        switch (op.getType()) {
            case INSERT_CHAR: {
                BlockNode block = blockCRDT.getBlock(op.getBlockId());
                if (block != null && !block.isDeleted()) {
                    block.getContent().applyRemoteInsert(op, op.isBold(), op.isItalic());
                }
                break;
            }
            case DELETE_CHAR: {
                BlockNode block = blockCRDT.getBlock(op.getBlockId());
                if (block != null) {
                    block.getContent().applyRemoteDelete(op);
                }
                break;
            }
            case INSERT_BLOCK:
                blockCRDT.applyRemoteInsertBlock(op);
                break;
            case DELETE_BLOCK:
                handleRemoteDeleteBlock(op);
                break;
            default:
                break;
        }
        // hnrefresh b3d ay remote operation
        refreshTextPane(0);
    }

    // Handle remote block deletion gracefully
    // If the user is currently inside the deleted block, move them to another
    private void handleRemoteDeleteBlock(CRDTOperation op) {
        blockCRDT.applyRemoteDeleteBlock(op);

        // If the user was typing in the deleted block, move to first visible block
        if (op.getBlockId().equals(currentBlockId)) {
            java.util.List<BlockNode> visible = blockCRDT.getVisibleBlocks();
            if (!visible.isEmpty()) {
                currentBlockId = visible.get(0).getBlockId();
            }
        }
    }

    // hna hnhandle el bold w el italic buttons
    private void onBoldClicked() {
        isBold = !isBold;
        statusLabel.setText("  Formatting: " + (isBold ? "BOLD " : "") + (isItalic ? "ITALIC" : ""));
    }

    private void onItalicClicked() {
        isItalic = !isItalic;
        statusLabel.setText("  Formatting: " + (isBold ? "BOLD " : "") + (isItalic ? "ITALIC" : ""));
    }

    // Getters — kareem will need these to attach cursor highlighters,...
    public JTextPane getTextPane()       { return textPane;       }
    public BlockCRDT getBlockCRDT()     { return blockCRDT;      }
    public String    getSessionId()     { return sessionId;      }
    public int       getSiteId()        { return siteId;         }
    public int       getClock()         { return clock;          }
    public void      setClock(int c)    { this.clock = c;        }

    // start editor
    public static void main(String[] args) {
        int site = 1;
        if (args.length > 0) {
            try { site = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        final int finalSite = site;
        // Swing threading logic
        SwingUtilities.invokeLater(() -> new EditorUI(finalSite));
    }
}