import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionManager handles user authentication, session creation, and permissions.
 * 
 * Responsibilities:
 * - Generate shareable codes (Editor/Viewer roles)
 * - Track user sessions and their permissions
 * - Enforce role-based access control
 * - Track active users per document
 */
public class SessionManager {
    private final ConcurrentHashMap<String, UserSession> allSessions;
    private final ConcurrentHashMap<String, ShareableCode> shareableCodes;
    private final ConcurrentHashMap<String, List<UserSession>> documentUsers;
    private int codeCounter = 1000; // For generating shareable codes

    public SessionManager() {
        this.allSessions = new ConcurrentHashMap<>();
        this.shareableCodes = new ConcurrentHashMap<>();
        this.documentUsers = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new user session.
     * 
     * @param userId     Unique user ID
     * @param documentId Document ID they're accessing
     * @param role       User role (EDITOR or VIEWER)
     * @return UserSession object
     */
    public UserSession createSession(String userId, String documentId, UserRole role) {
        UserSession session = new UserSession(userId, documentId, role);
        allSessions.put(userId, session);

        // Add user to document user list
        List<UserSession> users = documentUsers.computeIfAbsent(documentId, 
            key -> Collections.synchronizedList(new ArrayList<>()));
        users.add(session);

        System.out.println("Session created: " + userId + " in document " + 
            documentId + " as " + role);
        return session;
    }

    /**
     * Closes a user session.
     */
    public void closeSession(String userId) {
        UserSession session = allSessions.remove(userId);
        if (session != null) {
            String documentId = session.getDocumentId();
            List<UserSession> users = documentUsers.get(documentId);
            if (users != null) {
                users.remove(session);
            }
            System.out.println("Session closed: " + userId);
        }
    }

    /**
     * Gets a user's session.
     */
    public UserSession getSession(String userId) {
        return allSessions.get(userId);
    }

    /**
     * Generates a shareable code for a document.
     * Each code can be for EDITOR or VIEWER role.
     * 
     * @param documentId Document to create code for
     * @param role       EDITOR or VIEWER
     * @return ShareableCode object with generated code string
     */
    public ShareableCode generateShareableCode(String documentId, UserRole role) {
        codeCounter++;
        String code = generateCode(documentId, role);
        ShareableCode shareCode = new ShareableCode(code, documentId, role);
        shareableCodes.put(code, shareCode);

        System.out.println("Generated " + role + " code: " + code + 
            " for document " + documentId);
        return shareCode;
    }

    /**
     * Validates and redeems a shareable code.
     * Returns the session if code is valid, null otherwise.
     */
    public UserSession redeemShareableCode(String code, String userId) {
        ShareableCode shareCode = shareableCodes.get(code);

        if (shareCode == null) {
            System.out.println("Invalid shareable code: " + code);
            return null;
        }

        if (shareCode.isExpired()) {
            System.out.println("Shareable code expired: " + code);
            shareableCodes.remove(code);
            return null;
        }

        if (shareCode.isUsed()) {
            // Shareable codes can be reused multiple times or limit to one use
            // Depending on requirements. For now, allow multiple uses.
        }

        // Create a new session with the code's role and document
        UserSession session = createSession(userId, shareCode.getDocumentId(), shareCode.getRole());
        shareCode.recordUse();
        return session;
    }

    /**
     * Revokes (invalidates) a shareable code.
     */
    public void revokeShareableCode(String code) {
        ShareableCode removed = shareableCodes.remove(code);
        if (removed != null) {
            System.out.println("Shareable code revoked: " + code);
        }
    }

    /**
     * Checks if a user has permission to perform an action.
     */
    public boolean hasPermission(String userId, Permission permission) {
        UserSession session = allSessions.get(userId);
        if (session == null) return false;

        UserRole role = session.getRole();
        switch (permission) {
            case EDIT:
                return role == UserRole.EDITOR;
            case VIEW:
                return role == UserRole.EDITOR || role == UserRole.VIEWER;
            case VIEW_SHAREABLE_CODES:
                return role == UserRole.EDITOR;
            case INVITE_USERS:
                return role == UserRole.EDITOR;
            default:
                return false;
        }
    }

    /**
     * Enforces permission check and throws exception if denied.
     */
    public void enforcePermission(String userId, Permission permission) 
            throws PermissionDeniedException {
        if (!hasPermission(userId, permission)) {
            throw new PermissionDeniedException(
                "User " + userId + " lacks permission: " + permission);
        }
    }

    /**
     * Gets all active users in a document.
     */
    public List<UserSession> getDocumentUsers(String documentId) {
        List<UserSession> users = documentUsers.get(documentId);
        return users != null ? new ArrayList<>(users) : new ArrayList<>();
    }

    /**
     * Gets all active users in a document with a specific role.
     */
    public List<UserSession> getDocumentUsersByRole(String documentId, UserRole role) {
        List<UserSession> allUsers = getDocumentUsers(documentId);
        List<UserSession> result = new ArrayList<>();
        for (UserSession user : allUsers) {
            if (user.getRole() == role) {
                result.add(user);
            }
        }
        return result;
    }

    /**
     * Gets count of active editors and viewers in a document.
     */
    public int getEditorCount(String documentId) {
        return getDocumentUsersByRole(documentId, UserRole.EDITOR).size();
    }

    public int getViewerCount(String documentId) {
        return getDocumentUsersByRole(documentId, UserRole.VIEWER).size();
    }

    /**
     * Generates a random shareable code string.
     */
    private String generateCode(String documentId, UserRole role) {
        // Format: [ROLE]-[DOCUMENT_PREFIX]-[RANDOM]
        // Example: EDITOR-DOC001-ABCD1234
        String rolePrefix = role == UserRole.EDITOR ? "EDIT" : "VIEW";
        String docPrefix = documentId.substring(0, Math.min(4, documentId.length())).toUpperCase();
        String random = generateRandomString(8);
        return rolePrefix + "-" + docPrefix + "-" + random;
    }

    /**
     * Generates a random alphanumeric string.
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

/**
 * Represents a user session with role and permissions.
 */
class UserSession {
    private final String userId;
    private final String documentId;
    private final UserRole role;
    private final long createdAt;
    private long lastActivity;

    public UserSession(String userId, String documentId, UserRole role) {
        this.userId = userId;
        this.documentId = documentId;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
    }

    public String getUserId() { return userId; }
    public String getDocumentId() { return documentId; }
    public UserRole getRole() { return role; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isStale(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity > timeoutMs;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "userId='" + userId + '\'' +
                ", documentId='" + documentId + '\'' +
                ", role=" + role +
                ", createdAt=" + createdAt +
                '}';
    }
}

/**
 * Represents a shareable code for inviting users to a document.
 */
class ShareableCode {
    private final String code;
    private final String documentId;
    private final UserRole role;
    private final long createdAt;
    private long expiresAt;
    private int useCount;

    public ShareableCode(String code, String documentId, UserRole role) {
        this.code = code;
        this.documentId = documentId;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
        // Expire after 7 days
        this.expiresAt = createdAt + (7 * 24 * 60 * 60 * 1000);
        this.useCount = 0;
    }

    public String getCode() { return code; }
    public String getDocumentId() { return documentId; }
    public UserRole getRole() { return role; }
    public int getUseCount() { return useCount; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isUsed() {
        return useCount > 0;
    }

    public void recordUse() {
        useCount++;
    }

    public void setExpiration(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "ShareableCode{" +
                "code='" + code + '\'' +
                ", documentId='" + documentId + '\'' +
                ", role=" + role +
                ", useCount=" + useCount +
                ", expired=" + isExpired() +
                '}';
    }
}

/**
 * User roles for permission control.
 */
enum UserRole {
    EDITOR,   // Can edit, view, and invite
    VIEWER    // Can only view, cannot edit
}

/**
 * Permissions that can be enforced.
 */
enum Permission {
    EDIT,
    VIEW,
    VIEW_SHAREABLE_CODES,
    INVITE_USERS
}

/**
 * Exception for permission violations.
 */
class PermissionDeniedException extends Exception {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
