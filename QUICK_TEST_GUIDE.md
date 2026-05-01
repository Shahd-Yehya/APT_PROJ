# Quick Test & Expected Output

## Test Scenario: Two Clients Syncing in Document "doc1"

### Terminal 1: Start Server
```bash
cd c:\Users\yassa\OneDrive\Dokumenti\GitHub\APT_PROJ
gradle runServer
```

**Expected Output:**
```
> Task :runServer
[Server] Listening on port 8080
```
*(Server waits here for client connections)*

---

### Terminal 2: Start Client 1
```bash
cd c:\Users\yassa\OneDrive\Dokumenti\GitHub\APT_PROJ
gradle runClient1
```

**UI appears with "Join Session" button.**

Click "Join Session" button, then accept default values (ws://localhost:8080, doc1):

**Expected Console Output:**
```
[EditorPanel] Joining session: server=ws://localhost:8080 documentId=doc1 siteId=0
[EditorPanel] Calling connect()...
[EditorPanel] Waiting for connection to establish (5 seconds timeout)...
[CollabClient] Waiting for connection (timeout=5000ms)...
[CollabClient] OPEN – site=0 documentId=doc1
[CollabClient] Sending JOIN: {"type":"JOIN","siteId":0,"documentId":"doc1"}
[CollabClient] Draining queued messages (queue size=1)
[CollabClient] DRAINING queued message #1: {"type":"INSERT_BLOCK",...}
[CollabClient] Drained 1 queued messages
[CollabClient] Connection established!
[EditorPanel] Connection established! Sending initial blocks...
[EditorPanel] Sending 1 initial block(s)
[CollabClient] Sending INSERT_BLOCK: {...}
```

**Server Console Should Show:**
```
[Server] Client connected: /127.0.0.1:XXXXX  total=1
[Server] RECV from /127.0.0.1:XXXXX: {"type":"JOIN",...}
[Server] Parsed – type=JOIN siteId=0 docId=doc1
[Server] >>> JOIN: Site 0 joined document 'doc1'
[Server]   Stored attachment for site 0
[Server]   Notified new user about 0 existing users
[Server] Relaying message to document 'doc1'
[Server] Relay summary: sent=0 filtered=0 noAttachment=0
[Server] RECV from /127.0.0.1:XXXXX: {"type":"INSERT_BLOCK",...}
[Server] Parsed – type=INSERT_BLOCK siteId=0 docId=doc1
[Server] Relaying message to document 'doc1'
[Server] Relay summary: sent=0 filtered=0 noAttachment=0
```

**Client 1 UI:**
- Dialog closes
- Editor is now ready for typing
- "Active Users" panel shows: "You (site 0)"

---

### Terminal 3: Start Client 2
```bash
cd c:\Users\yassa\OneDrive\Dokumenti\GitHub\APT_PROJ
gradle runClient2
```

**UI appears. Click "Join Session", accept defaults:**

**Expected Console Output (Client 2):**
```
[EditorPanel] Joining session: server=ws://localhost:8080 documentId=doc1 siteId=1
[EditorPanel] Calling connect()...
[CollabClient] Waiting for connection (timeout=5000ms)...
[CollabClient] OPEN – site=1 documentId=doc1
[CollabClient] Sending JOIN: {"type":"JOIN","siteId":1,"documentId":"doc1"}
[CollabClient] Draining queued messages (queue size=1)
[CollabClient] DRAINING queued message #1: {"type":"INSERT_BLOCK",...}
[CollabClient] Connection established!
[EditorPanel] Connection established! Sending initial blocks...
[CollabClient] RECV: {"type":"JOIN",...}
[CollabClient] Parsed message type=JOIN from site=0
```

**Server Console Should Show:**
```
[Server] Client connected: /127.0.0.1:YYYY  total=2
[Server] RECV from /127.0.0.1:YYYY: {"type":"JOIN",...}
[Server] Parsed – type=JOIN siteId=1 docId=doc1
[Server] >>> JOIN: Site 1 joined document 'doc1'
[Server]   Stored attachment for site 1
[Server]   Notifying new user about site 0
[Server]   Notified new user about 1 existing users
[Server] Relaying message to document 'doc1'
[Server]   Skipping sender
[Server]   Relaying to site 0 (docId match)
[Server] Relay summary: sent=1 filtered=0 noAttachment=0
```

**Client 1 Console Should Show:**
```
[CollabClient] RECV: {"type":"JOIN",...}
[CollabClient] Parsed message type=JOIN from site=1
```

**Client 1 UI:**
- "Active Users" panel now shows:
  - "You (site 0)"
  - "User (site 1)"

**Client 2 UI:**
- "Active Users" panel now shows:
  - "You (site 1)"
  - "User (site 0)"

---

## Test: Text Synchronization

### Client 1: Type "hello"

**Client 1 Console:**
```
[CollabClient] Sending INSERT_CHAR: {"type":"INSERT_CHAR","siteId":0,"charSite":0,"charValue":"h",...}
[CollabClient] Sending INSERT_CHAR: {"type":"INSERT_CHAR","siteId":0,"charSite":0,"charValue":"e",...}
[CollabClient] Sending INSERT_CHAR: {"type":"INSERT_CHAR","siteId":0,"charSite":0,"charValue":"l",...}
[CollabClient] Sending INSERT_CHAR: {"type":"INSERT_CHAR","siteId":0,"charSite":0,"charValue":"l",...}
[CollabClient] Sending INSERT_CHAR: {"type":"INSERT_CHAR","siteId":0,"charSite":0,"charValue":"o",...}
```

**Server Console:**
```
[Server] RECV from /127.0.0.1:XXXXX: {"type":"INSERT_CHAR",...,"charValue":"h",...}
[Server] Parsed – type=INSERT_CHAR siteId=0 docId=doc1
[Server] Relaying message to document 'doc1'
[Server]   Relaying to site 1 (docId match)
[Server] Relay summary: sent=1 filtered=0 noAttachment=0
[... repeated for e, l, l, o ...]
```

**Client 2 Console:**
```
[CollabClient] RECV: {"type":"INSERT_CHAR",...,"charValue":"h",...}
[CollabClient] Parsed message type=INSERT_CHAR from site=0
[CollabClient] RECV: {"type":"INSERT_CHAR",...,"charValue":"e",...}
[CollabClient] Parsed message type=INSERT_CHAR from site=0
[... etc for l, l, o ...]
```

**Client 2 UI:**
- Text editor shows "hello" appearing in real-time

---

## Test: Connection Failure

### Terminal 2: Start Client 1 WITHOUT Server Running

**Client 1 Console:**
```
[EditorPanel] Joining session: server=ws://localhost:8080 documentId=doc1 siteId=0
[EditorPanel] Calling connect()...
[EditorPanel] Waiting for connection to establish (5 seconds timeout)...
[CollabClient] Waiting for connection (timeout=5000ms)...
[CollabClient] ERROR: WebSocket Error: Connection refused
[CollabClient] Connection timeout after 5000ms
[EditorPanel] Connection timeout – server at ws://localhost:8080 did not respond within 5 seconds.
```

**Client 1 UI:**
- Dialog shows: "Connection timeout – server at ws://localhost:8080 did not respond within 5 seconds."
- User can retry

---

## Checklist: What Should Work

✅ Server starts and listens on port 8080  
✅ Client 1 connects and shows in Active Users  
✅ Client 2 joins and sees Client 1 in Active Users  
✅ Client 1 sees Client 2 appear in Active Users  
✅ Text typed in Client 1 appears in Client 2  
✅ Text typed in Client 2 appears in Client 1  
✅ Cursor movement from Client 1 shows colored bar in Client 2  
✅ Cursor movement from Client 2 shows colored bar in Client 1  
✅ When Client 1 closes, it disappears from Client 2's Active Users  
✅ When Client 2 closes, it disappears from Client 1's Active Users  
✅ Connection timeout error shows UI dialog if server is down  

---

## If Something Doesn't Work

1. **Check server is running**: Look for `[Server] Listening on port 8080`
2. **Check client connected**: Look for `[CollabClient] OPEN – site=X`
3. **Check messages being relayed**: Look for `[Server] Relay summary: sent=X`
4. **Check message reception**: Look for `[CollabClient] RECV:`
5. **Check parse errors**: Look for `[CollabClient] PARSE ERROR:` in red
6. **Check network errors**: Look for `[CollabClient] ERROR:` in red

**All debug output is now VISIBLE** – you should be able to trace the exact flow of each message through the system.

