import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user sessions and permissions.
 */
public class UserSessionManager {
    private final ConcurrentHashMap<String, UserSession> sessions;
    private final ConcurrentHashMap<String, ShareableCode> shareableCodes;
    private final ConcurrentHashMap<String, List<String>> documentUsers;
    private int codeCounter = 0;

    public UserSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.shareableCodes = new ConcurrentHashMap<>();
        this.documentUsers = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new user session.
     */
    public UserSession createSession(String userId, String documentId) {
        String sessionId = generateSessionId();
        UserSession session = new UserSession(sessionId, userId, documentId);
        sessions.put(sessionId, session);

        // Add user to document's user list
        documentUsers.computeIfAbsent(documentId, k -> 
            Collections.synchronizedList(new ArrayList<>())).add(userId);

        System.out.println("User session created: " + userId + " in document " + documentId);
        return session;
    }

    /**
     * Ends a user session.
     */
    public void endSession(String sessionId) {
        UserSession session = sessions.remove(sessionId);
        if (session != null) {
            List<String> users = documentUsers.get(session.getDocumentId());
            if (users != null) {
                users.remove(session.getUserId());
            }
            System.out.println("User session ended: " + sessionId);
        }
    }

    /**
     * Gets a session by ID.
     */
    public UserSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Validates session exists and is active.
     */
    public boolean isSessionValid(String sessionId) {
        UserSession session = sessions.get(sessionId);
        return session != null && !session.isExpired();
    }

    /**
     * Generates a shareable code for a document.
     * Code grants access with specified role (EDITOR or VIEWER).
     */
    public ShareableCode generateShareableCode(String documentId, String role) {
        String code = generateCode();
        ShareableCode shareCode = new ShareableCode(code, documentId, role);
        shareableCodes.put(code, shareCode);

        System.out.println("Shareable code generated: " + code + 
            " for document " + documentId + " with role " + role);
        return shareCode;
    }

    /**
     * Validates a shareable code.
     */
    public boolean isCodeValid(String code) {
        ShareableCode shareCode = shareableCodes.get(code);
        return shareCode != null && !shareCode.isExpired();
    }

    /**
     * Gets the role granted by a shareable code.
     */
    public String getCodeRole(String code) {
        ShareableCode shareCode = shareableCodes.get(code);
        if (shareCode != null && !shareCode.isExpired()) {
            return shareCode.getRole();
        }
        return null;
    }

    /**
     * Gets document ID from a shareable code.
     */
    public String getDocumentFromCode(String code) {
        ShareableCode shareCode = shareableCodes.get(code);
        if (shareCode != null && !shareCode.isExpired()) {
            return shareCode.getDocumentId();
        }
        return null;
    }

    /**
     * Revokes a shareable code.
     */
    public void revokeCode(String code) {
        ShareableCode removed = shareableCodes.remove(code);
        if (removed != null) {
            System.out.println("Code revoked: " + code);
        }
    }

    /**
     * Gets all active users in a document.
     */
    public List<String> getDocumentUsers(String documentId) {
        List<String> users = documentUsers.get(documentId);
        return users != null ? new ArrayList<>(users) : new ArrayList<>();
    }

    /**
     * Gets active user count for a document.
     */
    public int getDocumentUserCount(String documentId) {
        List<String> users = documentUsers.get(documentId);
        return users != null ? users.size() : 0;
    }

    /**
     * Checks if user has permission to edit a document.
     */
    public boolean canUserEdit(String sessionId) {
        UserSession session = sessions.get(sessionId);
        if (session == null) return false;
        return session.getRole().equals("EDITOR");
    }

    /**
     * Checks if user has permission to view a document.
     */
    public boolean canUserView(String sessionId) {
        UserSession session = sessions.get(sessionId);
        if (session == null) return false;
        return session.getRole().equals("EDITOR") || session.getRole().equals("VIEWER");
    }

    /**
     * Sets user role for a session.
     */
    public void setUserRole(String sessionId, String role) {
        UserSession session = sessions.get(sessionId);
        if (session != null) {
            session.setRole(role);
        }
    }

    /**
     * Gets user role for a session.
     */
    public String getUserRole(String sessionId) {
        UserSession session = sessions.get(sessionId);
        if (session != null) {
            return session.getRole();
        }
        return null;
    }

    /**
     * Generates a unique session ID.
     */
    private String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis() + "_" + 
            (int)(Math.random() * 10000);
    }

    /**
     * Generates a shareable code.
     */
    private String generateCode() {
        codeCounter++;
        return "CODE_" + codeCounter + "_" + 
            System.currentTimeMillis() % 100000;
    }

    /**
     * Gets total active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Gets all active sessions.
     */
    public Collection<UserSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * Inner class: Represents a user session connected to the collaborative editor.
     */
    public static class UserSession {
        private final String sessionId;
        private final String userId;
        private final String documentId;
        private String role; // "EDITOR" or "VIEWER"
        private final long createdAt;
        private long lastActivity;
        private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

        public UserSession(String sessionId, String userId, String documentId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.documentId = documentId;
            this.role = "EDITOR"; // Default role
            this.createdAt = System.currentTimeMillis();
            this.lastActivity = createdAt;
        }

        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getDocumentId() { return documentId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public long getCreatedAt() { return createdAt; }
        public long getLastActivity() { return lastActivity; }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - lastActivity) > SESSION_TIMEOUT_MS;
        }

        @Override
        public String toString() {
            return "UserSession{" +
                    "sessionId='" + sessionId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", documentId='" + documentId + '\'' +
                    ", role='" + role + '\'' +
                    ", lastActivity=" + lastActivity +
                    '}';
        }
    }

    /**
     * Inner class: Represents a shareable code that grants access to a document.
     */
    public static class ShareableCode {
        private final String code;
        private final String documentId;
        private final String role; // "EDITOR" or "VIEWER"
        private final long createdAt;
        private static final long CODE_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

        public ShareableCode(String code, String documentId, String role) {
            this.code = code;
            this.documentId = documentId;
            this.role = role;
            this.createdAt = System.currentTimeMillis();
        }

        public String getCode() { return code; }
        public String getDocumentId() { return documentId; }
        public String getRole() { return role; }
        public long getCreatedAt() { return createdAt; }

        public boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > CODE_EXPIRATION_MS;
        }

        @Override
        public String toString() {
            return "ShareableCode{" +
                    "code='" + code + '\'' +
                    ", documentId='" + documentId + '\'' +
                    ", role='" + role + '\'' +
                    ", createdAt=" + createdAt +
                    '}';
        }
    }
}
