# Phase 2 – UI + Networking
## Person 2: Cursor Tracking + UI–Network Integration

---

## Files you deliver

| File | What it does |
|------|-------------|
| `NetworkMessage.java` | JSON protocol – every message type |
| `CollabClient.java` | WebSocket client – connects to server, sends/receives |
| `CursorTracker.java` | Paints remote cursors in the JTextPane |
| `EditorPanel.java` | Full UI (Person 1 + Person 2 integration) |
| `CollabServer.java` | Relay server – broadcasts messages between clients |
| `Phase2ManualTest.java` | Local tests, no server needed |

---

## How to build

```bash
cd phase2
chmod +x build.sh
./build.sh
```

This downloads 3 JARs (~1 MB total) and compiles all `.java` files.

---

## How to run

### Step 1 – Start the server
```bash
java -cp out:lib/* CollabServer 8080
```

### Step 2 – Start two (or more) editor clients in separate terminals
```bash
# Terminal A
java -cp out:lib/* EditorPanel 1

# Terminal B
java -cp out:lib/* EditorPanel 2
```

### Step 3 – Join a session in each client
- Click **"Join Session"**
- Server URL: `ws://localhost:8080`
- Document ID: `doc1` (same in both)
- Click OK

Now type in either window. Changes appear in the other in real time.
Move your cursor – the other window shows a colored cursor bar.

---

## How to test

### Automated (no server needed)
```bash
java -cp out:lib/* Phase2ManualTest
```
Expected output:
```
===== Phase 2 Manual Tests =====

[TEST 1] NetworkMessage JSON round-trip
  PASS

[TEST 2] CURSOR message round-trip
  PASS

[TEST 3] CursorTracker.handleBlockDeleted()
  PASS – no crash, cursor gracefully reset

[TEST 4] Remote INSERT_CHAR applied to CRDT
  PASS – B sees: "H"

[TEST 5] DELETE_BLOCK while remote cursor is inside
  PASS – no crash on DELETE_BLOCK with cursor inside

===== All tests passed =====
```

### Manual test checklist

| # | Action | Expected |
|---|--------|----------|
| 1 | Type in client A | Text appears in client B immediately |
| 2 | Type in client B at same time as A | Both see merged text (CRDT resolves) |
| 3 | Move cursor in A | Colored bar appears in B at same position |
| 4 | Move cursor in B | Different-colored bar appears in A |
| 5 | Bold/Italic button | Formatting synced to other clients |
| 6 | Undo in A | Change undone, reflected in B |
| 7 | Close client B | B's cursor bar disappears from A |
| 8 | Restart client B and rejoin | Works cleanly |

---

## Cursor colors

| siteId | Color |
|--------|-------|
| 0 | Blue (local – never shown) |
| 1 | Red |
| 2 | Green |
| 3 | Orange |

---

## Edge case: DELETE_BLOCK with cursor inside

When a `DELETE_BLOCK` message arrives:

1. `EditorPanel.handleNetworkMessage()` calls  
   `cursorTracker.handleBlockDeleted(blockSite, blockClock)` **before** applying the op.
2. `CursorTracker.handleBlockDeleted()` checks every remote cursor:  
   if it's inside the deleted block, its position is reset to 0 (start of doc).
3. Then `doc.applyRemoteDeleteBlock(op)` is called.
4. The block hides gracefully — **no crash, no stale highlight**.

---

## Dependencies

- `Java-WebSocket 1.5.3` – WebSocket client + server  
- `org.json 20231013` – JSON serialisation  
- `SLF4J 2.0.9` – logging (required by Java-WebSocket)

All downloaded automatically by `build.sh`.
