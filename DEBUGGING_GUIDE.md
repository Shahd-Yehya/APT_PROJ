# WebSocket Sync Debugging Guide

## What Was Fixed

### 1. **Race Condition: Operations Sent Before Connection Ready**
- **Problem**: `EditorPanel` called `client.sendOperation()` immediately after `connect()`, before the WebSocket was actually established
- **Fix**: Added `waitForConnection(5000ms)` blocking call that waits for the `onOpen()` callback
- **File**: `EditorPanel.java` → `showJoinDialog()` method

### 2. **Connection Errors Hidden from User**
- **Problem**: WebSocket errors printed to stderr but never shown in UI
- **Fix**: 
  - Added `setOnError()` callback method to `CollabClient`
  - Connected it to `onError()` which now calls the callback
  - `EditorPanel` shows error dialogs via Swing
- **Files**: `CollabClient.java`, `EditorPanel.java`

### 3. **Silent Message Relay Failures in Server**
- **Problem**: Server filtered messages without logging, so hidden failures were invisible
- **Fix**: Added detailed logging to `relayToDocument()` showing:
  - Which clients matched the documentId filter
  - Which clients were skipped and why
  - Count of relayed/filtered/missing messages
- **File**: `CollabServer.java`

### 4. **Incomplete LEAVE Message Handling**
- **Problem**: On disconnect, synthetic LEAVE with siteId=-1 was created if attachment was null
- **Fix**: Store JOIN message as attachment, use it to build proper LEAVE on disconnect
- **File**: `CollabServer.java` → `onClose()` method

### 5. **No Visibility into Message Flow**
- **Problem**: When sync fails, no logs show where messages are getting stuck
- **Fix**: Added logging at every stage:
  - `sendRaw()`: logs if message is sent or queued
  - `onMessage()`: logs every received message and parse results
  - `drainQueue()`: logs queue size and each drained message
  - `onOpen()`: logs connection establishment
  - `onError()`: logs errors with full exception details
- **Files**: `CollabClient.java`, `CollabServer.java`

---

## How to Test the Fixes

### Step 1: Start the Server
Open one terminal and run:
```bash
gradle runServer
```

Expected output:
```
[Server] Listening on port 8080
```

### Step 2: Start Client 1
Open another terminal and run:
```bash
gradle runClient1
```

Expected output:
```
[EditorPanel] Joining session: server=ws://localhost:8080 documentId=doc1 siteId=0
[CollabClient] Calling connect()...
[CollabClient] Waiting for connection (timeout=5000ms)...
[CollabClient] OPEN – site=0 documentId=doc1
[CollabClient] Sending JOIN: {"type":"JOIN","siteId":0,"documentId":"doc1"}
...
```

### Step 3: Start Client 2
Open a third terminal and run:
```bash
gradle runClient2
```

### Step 4: Test Text Sync
1. **Client 1**: Type "hello" in the text editor
2. **Client 2**: Should immediately see "hello" appear
3. **Check Server Logs**: Should show:
   - `[Server] RECV: ...INSERT_CHAR...`
   - `[Server] Relaying message to document 'doc1'`
   - `[Server] Relay summary: sent=1 filtered=0 noAttachment=0`

### Step 5: Test User Presence
1. **Client 2**: Look at "Active Users" panel on the right
2. **Should see**: "You (site 1)" and "User (site 0)"
3. **Client 1**: Should see "You (site 0)" and "User (site 1)"
4. **Check Server Logs**: Should show JOIN messages being relayed

### Step 6: Test Cursor Movement
1. **Client 1**: Move the cursor around
2. **Check Server Logs**: Should show:
   - `[Server] RECV: ...CURSOR...`
   - `[Server] Relaying message to document 'doc1'`
3. **Client 2**: Should see a colored cursor bar from Client 1

### Step 7: Test Disconnection
1. **Client 1**: Close the window
2. **Check Server Logs**: Should show:
   - `[Server] CLOSE: ... code=1000 reason=null`
   - `[Server] Broadcasting LEAVE for site 0 from document 'doc1'`
   - `[Server] Relay summary: sent=1`
3. **Client 2**: "User (site 0)" should disappear from Active Users panel

---

## Debug Output Examples

### Successful Connection (Client)
```
[EditorPanel] Joining session: server=ws://localhost:8080 documentId=doc1 siteId=0
[EditorPanel] Calling connect()...
[EditorPanel] Waiting for connection (timeout=5000ms)...
[CollabClient] Waiting for connection (timeout=5000ms)...
[CollabClient] OPEN – site=0 documentId=doc1
[CollabClient] Sending JOIN: {"type":"JOIN","siteId":0,"documentId":"doc1"}
[CollabClient] Draining queued messages (queue size=1)
[CollabClient] DRAINING queued message #1: {"type":"INSERT_BLOCK",...}
[CollabClient] Drained 1 queued messages
[CollabClient] Connection established!
[EditorPanel] Connection established! Sending initial blocks...
[EditorPanel] Sending 1 initial block(s)
```

### Successful Message Relay (Server)
```
[Server] RECV from /127.0.0.1:54321: {"type":"INSERT_CHAR","siteId":0,...}
[Server] Parsed – type=INSERT_CHAR siteId=0 docId=doc1
[Server] Relaying message to document 'doc1'
[Server]   Skipping sender
[Server]   Relaying to site 1 (docId match)
[Server] Relay summary: sent=1 filtered=0 noAttachment=0
```

### Message Received (Client)
```
[CollabClient] RECV: {"type":"INSERT_CHAR","siteId":0,...}
[CollabClient] Parsed message type=INSERT_CHAR from site=0
[EditorPanel] Handling INSERT_CHAR from site 0
[EditorPanel] Refreshing text pane...
```

---

## If Sync Still Doesn't Work: Troubleshooting

### Issue: "Connection timeout – server at ws://localhost:8080 did not respond"
**Cause**: Server not running or not listening on port 8080

**Fix**: 
1. Check if server terminal shows `[Server] Listening on port 8080`
2. Check if there's a firewall blocking localhost:8080
3. Try using IP address instead: `ws://127.0.0.1:8080`

### Issue: Clients connect but don't sync
**Cause**: Messages being filtered or silently dropped

**Debug**: Look for these server messages:
```
[Server] Relay summary: sent=0 filtered=X noAttachment=Y
```

**If `filtered=X` (non-zero)**:
- Recipients have wrong documentId
- Verify both clients used the same documentId in the dialog

**If `noAttachment=Y` (non-zero)**:
- Recipients haven't sent JOIN yet
- This shouldn't happen with the new code, but check timestamps

### Issue: Cursors don't appear
**Cause**: CURSOR messages not being parsed correctly

**Debug**: Look for:
```
[CollabClient] RECV: {"type":"CURSOR",...}
[CollabClient] Parsed message type=CURSOR
```

If you don't see `CURSOR` messages, the relay might be dropping them.

### Issue: Users list doesn't update
**Cause**: JOIN messages not being relayed

**Debug**: Look for:
```
[Server] >>> JOIN: Site X joined document 'docX'
[Server] Relay summary: sent=1
```

---

## Console Log Levels

- **[CollabClient]**: Client-side WebSocket events
- **[EditorPanel]**: UI-level events and decisions  
- **[Server]**: Server relay and routing logic
- **System.err.println()**: Errors and warnings (appears in red in terminal)
- **System.out.println()**: Info and debug (appears in normal console)

Look for red text (stderr) first when troubleshooting.

---

## Next Steps

Once sync is working:
1. Test with multiple documents (different documentId values)
2. Test concurrent edits and conflict resolution
3. Add timeout/reconnection logic if connection drops
4. Remove debug logging once confident the system is stable

