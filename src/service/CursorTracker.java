package service;
import model.*;
import repository.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CursorTracker
 * =============
 * Renders the cursors of remote users inside a JTextPane using Highlighter.
 *
 * Each remote siteId gets a fixed color:
 *   site 0  →  blue   (local user – never shown)
 *   site 1  →  red
 *   site 2  →  green
 *   site 3  →  orange
 *
 * How it works
 * ------------
 *  1. The local caret moves  →  EditorPanel calls sendCursor().
 *  2. A CURSOR message arrives from the server →  updateCursor() is called.
 *  3. CursorTracker paints a 2-pixel-wide colored bar at that position using
 *     a custom Highlighter.HighlightPainter.
 *  4. After every local or remote document change, refreshAll() re-maps every
 *     stored visible-char index back to a Swing document offset and repaints.
 */
public class CursorTracker {

    // ─── colors (index = siteId) ──────────────────────────────────────────
    private static final Color[] SITE_COLORS = {
        new Color(0x2196F3),   // blue   (site 0 – local)
        new Color(0xF44336),   // red    (site 1)
        new Color(0x4CAF50),   // green  (site 2)
        new Color(0xFF9800),   // orange (site 3)
    };

    private static Color colorFor(int siteId) {
        if (siteId >= 0 && siteId < SITE_COLORS.length)
            return SITE_COLORS[siteId];
        // fallback for > 4 users: cycle through HSB
        float hue = (siteId * 0.618033988f) % 1f;
        return Color.getHSBColor(hue, 0.7f, 0.9f);
    }

    // ─── stored cursor state per remote site ─────────────────────────────
    private static class RemoteCursor {
        int visibleCharIndex;          // position inside the full visible doc
        int blockSite, blockClock;     // which block (for DELETE_BLOCK edge case)
        Object highlightTag;           // opaque handle returned by Highlighter.addHighlight
    }

    private final Map<Integer, RemoteCursor> cursors = new HashMap<>();

    // ─── refs ────────────────────────────────────────────────────────────
    private final JTextPane textPane;
    private final repository.Document  crdtDoc;   // your Phase-1 Document
    private final int       localSite;

    public CursorTracker(JTextPane textPane, repository.Document crdtDoc, int localSite) {
        this.textPane  = textPane;
        this.crdtDoc   = crdtDoc;
        this.localSite = localSite;
    }

    // ─── public API ───────────────────────────────────────────────────────

    /**
     * Called when a CURSOR message arrives for a remote user.
     *
     * @param remoteSiteId       who moved
     * @param visibleCharIndex   their caret position in the visible document
     * @param blockSite          their block's siteId
     * @param blockClock         their block's clock
     */
    public void updateCursor(int remoteSiteId, int visibleCharIndex,
                             int blockSite, int blockClock) {
        if (remoteSiteId == localSite) return;   // never render local cursor

        RemoteCursor rc = cursors.computeIfAbsent(
                remoteSiteId, k -> new RemoteCursor());
        rc.visibleCharIndex = visibleCharIndex;
        rc.blockSite        = blockSite;
        rc.blockClock       = blockClock;

        repaintCursor(remoteSiteId, rc);
    }

    /**
     * Called whenever the document content changes (local or remote) so that
     * all cursor bars are repositioned to match the new text layout.
     */
    public void refreshAll() {
        for (Map.Entry<Integer, RemoteCursor> entry : cursors.entrySet()) {
            repaintCursor(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Handle the DELETE_BLOCK edge case:
     * if any remote cursor is inside the deleted block, move it to position 0.
     *
     * @param blockSite  the deleted block's siteId
     * @param blockClock the deleted block's clock
     */
    public void handleBlockDeleted(int blockSite, int blockClock) {
        for (Map.Entry<Integer, RemoteCursor> entry : cursors.entrySet()) {
            RemoteCursor rc = entry.getValue();
            if (rc.blockSite == blockSite && rc.blockClock == blockClock) {
                // Move the cursor to the beginning of the document gracefully
                rc.visibleCharIndex = 0;
                rc.blockSite        = -1;
                rc.blockClock       = -1;
                repaintCursor(entry.getKey(), rc);
            }
        }
    }

    /** Remove a user's cursor when they leave. */
    public void removeCursor(int siteId) {
        RemoteCursor rc = cursors.remove(siteId);
        if (rc != null && rc.highlightTag != null) {
            textPane.getHighlighter().removeHighlight(rc.highlightTag);
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────

    private void repaintCursor(int siteId, RemoteCursor rc) {
        // Remove old highlight
        if (rc.highlightTag != null) {
            textPane.getHighlighter().removeHighlight(rc.highlightTag);
            rc.highlightTag = null;
        }

        // Convert visible-char index to Swing document offset
        int swingOffset = visibleIndexToSwingOffset(rc.visibleCharIndex);
        if (swingOffset < 0) return;

        Color color = colorFor(siteId);
        Highlighter.HighlightPainter painter = new CursorPainter(color);

        try {
            // Highlight a zero-width range – the painter draws the caret bar
            rc.highlightTag = textPane.getHighlighter()
                    .addHighlight(swingOffset, swingOffset, painter);
        } catch (BadLocationException e) {
            System.err.println("[CursorTracker] Bad offset " + swingOffset
                    + " for site " + siteId);
        }
    }

    /**
     * Convert a visible-character index (0-based in the CRDT document) to a
     * Swing StyledDocument offset.
     *
     * The CRDT document text is the concatenation of all visible blocks
     * separated by '\n'. The Swing document mirrors this exactly after each
     * refresh, so index maps 1-to-1.
     */
    private int visibleIndexToSwingOffset(int idx) {
        String fullText = crdtDoc.getFullText();
        if (idx < 0)              return 0;
        if (idx > fullText.length()) idx = fullText.length();
        return idx;
    }

    // ─── inner painter ────────────────────────────────────────────────────

    /**
     * Draws a thin vertical bar at the caret position instead of shading a
     * selection range.
     */
    private static class CursorPainter implements Highlighter.HighlightPainter {
        private final Color color;

        CursorPainter(Color color) { this.color = color; }

        @Override
        public void paint(Graphics g, int p0, int p1,
                          Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView(p0);
                if (r == null) return;
                g.setColor(color);
                // Draw a 2-pixel wide vertical bar the height of the line
                g.fillRect(r.x, r.y, 2, r.height);
            } catch (BadLocationException e) {
                // ignore – text changed between paint call
            }
        }
    }
}
