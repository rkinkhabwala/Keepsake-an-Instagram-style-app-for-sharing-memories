package com.keepsake.album.entity;

/**
 * The viewer's standing in an album. OWNER and EDITOR may add their own memories; VIEWER collaborators may only
 * look; NONE means the viewer sees the album only through its visibility setting.
 */
public enum AlbumRole {
    OWNER,
    EDITOR,
    VIEWER,
    NONE;

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean isCollaborator() {
        return this == EDITOR || this == VIEWER;
    }
}
