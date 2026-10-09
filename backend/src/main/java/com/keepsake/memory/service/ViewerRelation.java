package com.keepsake.memory.service;

import com.keepsake.user.entity.FollowStatus;

/**
 * How a viewer relates to a memory's author.
 *
 * @param followStatus        the viewer's follow of the author, or {@code null} when there is none
 * @param closeFriend         whether the viewer is in the author's close friends
 * @param viewerBlockedAuthor whether the viewer has blocked the author
 * @param authorBlockedViewer whether the author has blocked the viewer
 */
public record ViewerRelation(
        FollowStatus followStatus,
        boolean closeFriend,
        boolean viewerBlockedAuthor,
        boolean authorBlockedViewer
) {

    public static final ViewerRelation NONE = new ViewerRelation(null, false);

    public ViewerRelation(FollowStatus followStatus, boolean closeFriend) {
        this(followStatus, closeFriend, false, false);
    }

    boolean acceptedFollower() {
        return followStatus == FollowStatus.ACCEPTED;
    }

    /** A block in either direction. */
    boolean blocked() {
        return viewerBlockedAuthor || authorBlockedViewer;
    }
}
