# WebSocket Sync Issues - Root Cause Analysis

## Issues Identified

### 1. **RACE CONDITION: Operations Sent Before Connection Established**
**File**: `EditorPanel.java` (lines 542-554)
```java
client = new CollabClient(serverUrl, siteId, documentId, this::handleNetworkMessage);
client.connect();  // ← ASYNC - onOpen() called later
// ↓ These operations are sent IMMEDIATELY, before onOpen() is called
for (BlockNode bn : blocks) {
    CRDTOperation op = CRDTOperation.insertBlock(siteId, bn.getBlockId(), null);
    client.sendOperation(op);  // ← Will be QUEUED, not sent!
}
```

**Impact**: Operations are queued before the WebSocket is open. While `drainQueue()` should handle them after `onOpen()`, there's a brief window where the connection state is unclear.

**Fix**: Use a CountDownLatch or callback to wait for connection.

---

### 2. **Connection Errors Not Reported to User**
**Files**: `CollabClient.java` and `EditorPanel.java`
- `onError()` prints to stderr (not visible in UI)
- `EditorPanel.showJoinDialog()` doesn't capture connection failures

**Impact**: If connection fails, user just sees "Connecting to..." message indefinitely.

**Fix**: Add error callback and show error dialog.

---

### 3. **Missing/Incomplete Debug Logging**
**Issue**: When syncing fails, there's no visibility into:
- Whether connection was established
- Whether JOIN message was sent/received
- Whether relay filtering is dropping messages
- Whether messages are being queued

**Fix**: Add comprehensive logging at each stage.

---

### 4. **Potential Issue with Relay Filtering**
**File**: `CollabServer.java` (lines 82-100)
```java
private void relayToDocument(String message, WebSocket sender, String documentId) {
    for (WebSocket ws : connections) {
        if (ws != sender && ws.isOpen()) {
            String attachment = (String) ws.getAttachment();
            if (attachment != null) {
                try {
                    NetworkMessage wsMsg = NetworkMessage.fromJson(attachment);
                    if (wsMsg.getDocumentId().equals(documentId)) {
                        ws.send(message);
                    }
                } catch (Exception ignored) {}  // ← Silent failures!
            }
        }
    }
}
```

**Problem**: If a recipient hasn't sent a JOIN yet, attachment is null, and message is silently dropped.

**Fix**: Add logging for filtering decisions.

---

### 5. **LEAVE Message Reconstruction May Fail**
**File**: `CollabServer.java` (lines 36-47)
```java
String attachment = (String) conn.getAttachment();
NetworkMessage leave = NetworkMessage.fromJson(
    attachment != null ? attachment : "{\"type\":\"LEAVE\",\"siteId\":-1,\"documentId\":\"\"}");
relayToDocument(leave.toJson(), conn, leave.getDocumentId());
```

**Problem**: If attachment is null, the synthetic LEAVE has siteId=-1 which clients won't recognize.

**Fix**: Properly store JOIN info separately, or require explicit LEAVE on disconnect.

---

## Summary of Fixes

See the code fixes below. Key changes:

1. ✅ Add `CountDownLatch` to wait for connection before sending operations
2. ✅ Add error callback to show connection failures to user
3. ✅ Add comprehensive logging to all message send/receive points
4. ✅ Add logging to relay filtering logic
5. ✅ Fix LEAVE message handling

