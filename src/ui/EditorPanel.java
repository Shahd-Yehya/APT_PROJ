package ui;
import model.*;
import repository.*;
import service.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * EditorPanel
 * ===========
 * The main editor window.  This class covers both Person 1 and Person 2
 * responsibilities:
 *
 * Person 1 tasks implemented here
 * --------------------------------
 * - JTextPane-based editor
 * - Render text from getVisibleDocumentText() after every change
 * - Capture keyboard events → CRDT insert() / delete()
 * - Bold / Italic toolbar buttons
 * - Join Session dialog
 * - Active-users panel
 * - SwingUtilities.invokeLater() for all remote updates
 *
 * Person 2 tasks implemented here
 * --------------------------------
 * - Local cursor movement → CURSOR message via CollabClient.sendCursor()
 * - Incoming CURSOR messages → CursorTracker.updateCursor()
 * - Remote operations received → apply to CRDT → refresh UI
 * - DELETE_BLOCK with cursor inside → CursorTracker.handleBlockDeleted()
 * - Active-users panel update on JOIN / LEAVE
 */
public class EditorPanel extends JFrame {

    // ─── CRDT & networking ────────────────────────────────────────────────
    private final repository.Document    crdtDoc;
    private       CollabClient client;
    private final int         siteId;
    private       String      documentId = "default-doc";
    private       int         localClock = 0;
    private       boolean     activeBold = false;
    private       boolean     activeItalic = false;

    // ─── UI ──────────────────────────────────────────────────────────────
    private final JTextPane    textPane   = new JTextPane();
    private final JPanel       usersPanel = new JPanel();
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList  = new JList<>(usersModel);

    // ─── cursor tracking ─────────────────────────────────────────────────
    private CursorTracker cursorTracker;

    /** Prevents re-entrant document listener calls while we refresh text. */
    private volatile boolean updatingText = false;

    // ─── constructor ─────────────────────────────────────────────────────

    public EditorPanel(int siteId) {
        super("Collaborative Editor  (site=" + siteId + ")");
        this.siteId  = siteId;
        this.crdtDoc = new repository.Document(new BlockCRDT());

        // Seed an initial block so the user can start typing immediately
        CharacterId firstBlock = seedFirstBlock();

        cursorTracker = new CursorTracker(textPane, crdtDoc, siteId);

        buildUI();
        wireDocumentListener(firstBlock);
        wireCaretListener();
        setVisible(true);
    }

    // ─── UI construction ─────────────────────────────────────────────────

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLayout(new BorderLayout());

        // ── toolbar ──────────────────────────────────────────────────────
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton boldBtn   = new JButton("B");
        JButton italicBtn = new JButton("I");
        JButton joinBtn   = new JButton("Join Session");
        JButton undoBtn   = new JButton("Undo");
        JButton redoBtn   = new JButton("Redo");

        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD));
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC));

        boldBtn.addActionListener(e -> toggleFormat(true, false));
        italicBtn.addActionListener(e -> toggleFormat(false, true));
        joinBtn.addActionListener(e -> showJoinDialog());
        undoBtn.addActionListener(e -> doUndo());
        redoBtn.addActionListener(e -> doRedo());

        toolbar.add(boldBtn);
        toolbar.add(italicBtn);
        toolbar.addSeparator();
        toolbar.add(undoBtn);
        toolbar.add(redoBtn);
        toolbar.addSeparator();
        toolbar.add(joinBtn);

        add(toolbar, BorderLayout.NORTH);

        // ── text pane ─────────────────────────────────────────────────────
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(textPane);
        add(scroll, BorderLayout.CENTER);

        // ── users panel ──────────────────────────────────────────────────
        usersPanel.setLayout(new BorderLayout());
        usersPanel.setPreferredSize(new Dimension(140, 0));
        usersPanel.setBorder(BorderFactory.createTitledBorder("Active Users"));
        usersModel.addElement("You (site " + siteId + ")");
        usersPanel.add(new JScrollPane(usersList), BorderLayout.CENTER);
        add(usersPanel, BorderLayout.EAST);
    }

    // ─── keyboard → CRDT (Person 1) ──────────────────────────────────────

    /**
     * We intercept text changes via the Swing DocumentListener instead of
     * KeyListener, because KeyListener doesn't handle IME, paste, etc.
     */
    private void wireDocumentListener(CharacterId activeBlock) {
        // Store which block is currently active. For Phase 2, a single block
        // is fine – Phase 3 will add block splitting logic.
        final CharacterId[] currentBlock = {activeBlock};

        textPane.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (updatingText) return;
                try {
                    int    offset = e.getOffset();
                    int    len    = e.getLength();
                    String text   = e.getDocument()
                            .getText(offset, len);

                    for (int i = 0; i < text.length(); i++) {
                        char ch = text.charAt(i);
                        CharacterId parentId = crdtDoc
                                .getBlock(currentBlock[0])
                                .getContent()
                                .getParentIdForCursor(offset + i);

                        CRDTOperation op = crdtDoc
                                .getBlock(currentBlock[0])
                                .getContent()
                                .insert(siteId, ++localClock, ch,
                                        parentId, currentBlock[0],
                                        activeBold, activeItalic);

                        if (client != null && op != null)
                            client.sendOperation(op);
                    }
                    // No need to refresh text here – Swing already shows it
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (updatingText) return;
                int offset = e.getOffset();
                int len    = e.getLength();

                List<CharacterNode> visible = crdtDoc
                        .getBlock(currentBlock[0])
                        .getContent()
                        .getVisibleNodes();

                for (int i = offset + len - 1; i >= offset; i--) {
                    if (i < visible.size()) {
                        CharacterId cid = visible.get(i).getId();
                        CRDTOperation op = crdtDoc
                                .getBlock(currentBlock[0])
                                .getContent()
                                .delete(cid, currentBlock[0], siteId);

                        if (client != null && op != null)
                            client.sendOperation(op);
                    }
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) { /* styling – ignore */ }
        });
    }

    // ─── caret movement → CURSOR message (Person 2) ──────────────────────

    private void wireCaretListener() {
        textPane.addCaretListener(e -> {
            if (client == null || !client.isConnected()) return;

            int dot = e.getDot();  // caret position in Swing document

            // Find which block contains this offset
            List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
            int cumLen = 0;
            int bSite  = -1;
            int bClock = -1;

            for (BlockNode bn : blocks) {
                int blockLen = bn.getContent().visibleLength();
                if (dot <= cumLen + blockLen) {
                    bSite  = bn.getBlockId().getSiteId();
                    bClock = bn.getBlockId().getClock();
                    break;
                }
                cumLen += blockLen + 1; // +1 for the '\n' separator
            }

            client.sendCursor(dot, bSite, bClock);
        });
    }

    // ─── incoming network messages (Person 2) ────────────────────────────

    /**
     * All NetworkMessages flow through here.
     * This is ALWAYS called on the Swing event thread (guaranteed by CollabClient).
     */
    public void handleNetworkMessage(NetworkMessage msg) {
        switch (msg.getType()) {

            // ── remote CRDT operations ────────────────────────────────────
            case INSERT_CHAR:
            case DELETE_CHAR:
            case FORMAT_CHAR:
            case INSERT_BLOCK:
            case DELETE_BLOCK: {
                CRDTOperation op = msg.toCRDTOperation();
                if (op == null) break;

                applyRemoteOperation(op, msg);
                refreshTextPane();
                cursorTracker.refreshAll();
                break;
            }

            // ── remote cursor moved ───────────────────────────────────────
            case CURSOR: {
                cursorTracker.updateCursor(
                        msg.getSiteId(),
                        msg.getCursorPosition(),
                        msg.getCursorBlockSite(),
                        msg.getCursorBlockClock());
                break;
            }

            // ── user joined ───────────────────────────────────────────────
            case JOIN: {
                String label = "User (site " + msg.getSiteId() + ")";
                if (!usersModel.contains(label))
                    usersModel.addElement(label);
                break;
            }

            // ── user left ─────────────────────────────────────────────────
            case LEAVE: {
                cursorTracker.removeCursor(msg.getSiteId());
                usersModel.removeElement("User (site " + msg.getSiteId() + ")");
                break;
            }

            // ── full-document init from server ────────────────────────────
            case INIT: {
                // Phase 3 will deserialize full state here
                // For Phase 2 just refresh
                refreshTextPane();
                break;
            }

            default:
                break;
        }
    }

    // ─── apply a remote CRDTOperation ────────────────────────────────────

    private void applyRemoteOperation(CRDTOperation op, NetworkMessage msg) {
        CharacterId blockId = op.getBlockId();

        switch (op.getType()) {
            case INSERT_CHAR: {
                BlockNode bn = crdtDoc.getBlock(blockId);
                if (bn != null) {
                    bn.getContent().applyRemoteInsert(op, msg.isBold(), msg.isItalic());
                }
                break;
            }
            case DELETE_CHAR: {
                BlockNode bn = crdtDoc.getBlock(blockId);
                if (bn != null) {
                    bn.getContent().applyRemoteDelete(op);
                }
                break;
            }
            case FORMAT_CHAR: {
                BlockNode bn = crdtDoc.getBlock(blockId);
                if (bn != null) {
                    bn.getContent().applyRemoteFormat(op);
                }
                break;
            }
            case INSERT_BLOCK:
                crdtDoc.applyRemoteInsertBlock(op);
                break;
            case DELETE_BLOCK:
                // ── EDGE CASE: cursor inside the deleted block (Person 2) ──
                cursorTracker.handleBlockDeleted(
                        op.getBlockId().getSiteId(),
                        op.getBlockId().getClock());
                crdtDoc.applyRemoteDeleteBlock(op);
                break;
            default:
                break;
        }
    }

    // ─── refresh JTextPane from CRDT state (Person 1) ────────────────────

    /**
     * Rebuilds the JTextPane contents from the CRDT document text.
     * Must be called on the Swing event thread.
     * Uses SwingUtilities.invokeLater if not already on it.
     */
    public void refreshTextPane() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshTextPane);
            return;
        }

        updatingText = true;
        try {
            StyledDocument doc = textPane.getStyledDocument();
            // Store current selection/caret to restore later
            int caretPos = textPane.getCaretPosition();
            
            // Clear document
            doc.remove(0, doc.getLength());

            List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                List<CharacterNode> nodes = blocks.get(i).getContent().getVisibleNodes();
                for (CharacterNode node : nodes) {
                    SimpleAttributeSet attrs = new SimpleAttributeSet();
                    StyleConstants.setBold(attrs, node.isBold());
                    StyleConstants.setItalic(attrs, node.isItalic());
                    
                    doc.insertString(doc.getLength(), String.valueOf(node.getValue()), attrs);
                }
                
                // Add newline between blocks
                if (i < blocks.size() - 1) {
                    doc.insertString(doc.getLength(), "\n", null);
                }
            }

            // Restore caret safely
            int newLen = doc.getLength();
            textPane.setCaretPosition(Math.max(0, Math.min(caretPos, newLen)));

        } catch (BadLocationException e) {
            e.printStackTrace();
        } finally {
            updatingText = false;
        }
    }

    // ─── formatting (Person 1) ───────────────────────────────────────────

    private void toggleFormat(boolean toggleBold, boolean toggleItalic) {
        int start = textPane.getSelectionStart();
        int end   = textPane.getSelectionEnd();

        if (start == end) {
            // No selection: just toggle the active state for future typing
            if (toggleBold)   activeBold   = !activeBold;
            if (toggleItalic) activeItalic = !activeItalic;
            return;
        }

        // Apply formatting to selection across blocks
        List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
        int cumLen = 0;
        
        // We first determine the target state (toggle based on the first character of selection)
        boolean targetBold = activeBold;
        boolean targetItalic = activeItalic;
        
        for (BlockNode bn : blocks) {
            List<CharacterNode> nodes = bn.getContent().getVisibleNodes();
            for (int i = 0; i < nodes.size(); i++) {
                int globalPos = cumLen + i;
                if (globalPos >= start && globalPos < end) {
                    CharacterNode node = nodes.get(i);
                    
                    // Determine what to toggle to based on the first node in range
                    if (globalPos == start) {
                        if (toggleBold) targetBold = !node.isBold();
                        else targetBold = node.isBold();
                        
                        if (toggleItalic) targetItalic = !node.isItalic();
                        else targetItalic = node.isItalic();
                    }

                    CRDTOperation op = bn.getContent().format(
                            node.getId(), bn.getBlockId(), siteId, targetBold, targetItalic);
                    
                    if (client != null && op != null)
                        client.sendOperation(op);
                }
            }
            cumLen += nodes.size() + 1; // +1 for '\n'
        }
        
        refreshTextPane();
    }

    // ─── undo / redo (Person 1) ──────────────────────────────────────────

    private void doUndo() {
        List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
        if (blocks.isEmpty()) return;
        BlockNode bn = blocks.get(0);
        CRDTOperation op = bn.getContent().undo(bn.getBlockId());
        if (op != null && client != null) client.sendOperation(op);
        refreshTextPane();
        cursorTracker.refreshAll();
    }

    private void doRedo() {
        List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
        if (blocks.isEmpty()) return;
        BlockNode bn = blocks.get(0);
        CRDTOperation op = bn.getContent().redo(bn.getBlockId());
        if (op != null && client != null) client.sendOperation(op);
        refreshTextPane();
        cursorTracker.refreshAll();
    }

    // ─── Join Session dialog (Person 1) ──────────────────────────────────

    private void showJoinDialog() {
        JTextField serverField = new JTextField("ws://localhost:8080", 20);
        JTextField docField    = new JTextField(documentId, 20);

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Server URL:"));
        panel.add(serverField);
        panel.add(new JLabel("Document ID:"));
        panel.add(docField);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Join Session",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        documentId = docField.getText().trim();
        String serverUrl = serverField.getText().trim();

        try {
            client = new CollabClient(serverUrl, siteId, documentId,
                    this::handleNetworkMessage);
            client.connect();
            
            // After joining, broadcast our initial block so others know about it
            // (In a real app, we'd wait for connection, but for Phase 2 we'll send it now)
            List<BlockNode> blocks = crdtDoc.getVisibleBlocks();
            for (BlockNode bn : blocks) {
                CRDTOperation op = CRDTOperation.insertBlock(siteId, bn.getBlockId(), null);
                client.sendOperation(op);
            }

            JOptionPane.showMessageDialog(this,
                    "Connecting to " + serverUrl + " …",
                    "Join Session", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to connect: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    /** Creates the very first block so new users have somewhere to type. */
    private CharacterId seedFirstBlock() {
        CRDTOperation op = crdtDoc.insertBlock(siteId, ++localClock, null);
        if (op != null) return op.getBlockId();
        // If the op was null (duplicate), return root
        return BlockCRDT.ROOT_BLOCK_ID;
    }

    // ─── entry point ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Parse siteId from command line (default 0 = local dev)
        int site = 0;
        if (args.length > 0) {
            try { site = Integer.parseInt(args[0]); }
            catch (NumberFormatException ignored) {}
        }
        final int finalSite = site;
        SwingUtilities.invokeLater(() -> new EditorPanel(finalSite));
    }
}
