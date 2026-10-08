package com.keepsake.memory.service;

import com.keepsake.user.entity.FollowStatus;

/**
 * How a viewer relates to a memory's author.
 *
 * @param followStatus the viewer's follow of the author, or {@code null} when there is none
 * @param closeFriend  whether the viewer is in the author's close friends
 */
public record ViewerRelation(FollowStatus followStatus, boolean closeFriend) {

    public static final ViewerRelation NONE = new ViewerRelation(null, false);

    boolean acceptedFollower() {
        return followStatus == FollowStatus.ACCEPTED;
    }
}
