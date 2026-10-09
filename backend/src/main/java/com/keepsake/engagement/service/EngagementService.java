package com.keepsake.engagement.service;

import com.keepsake.common.error.NotFoundException;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.engagement.dto.CommentDto;
import com.keepsake.engagement.dto.CreateCommentRequest;
import com.keepsake.engagement.entity.Comment;
import com.keepsake.engagement.repository.CommentRepository;
import com.keepsake.engagement.repository.LikeRepository;
import com.keepsake.engagement.repository.SaveRepository;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Likes, comments, and saves. Every action first checks, through {@link MemoryService#requireVisible} and so
 * through MemoryAccessPolicy, that the viewer may see the memory; otherwise it is a 404.
 */
@Service
public class EngagementService {

    static final int MAX_COMMENT_LENGTH = 500;
    private static final UUID MIN_UUID = new UUID(0L, 0L);

    private final MemoryService memoryService;
    private final EngagementCounters counters;
    private final LikeRepository likeRepository;
    private final SaveRepository saveRepository;
    private final CommentRepository commentRepository;
    private final CommentAssembler commentAssembler;
    private final Clock clock;

    public EngagementService(
            MemoryService memoryService,
            EngagementCounters counters,
            LikeRepository likeRepository,
            SaveRepository saveRepository,
            CommentRepository commentRepository,
            CommentAssembler commentAssembler,
            Clock clock
    ) {
        this.memoryService = memoryService;
        this.counters = counters;
        this.likeRepository = likeRepository;
        this.saveRepository = saveRepository;
        this.commentRepository = commentRepository;
        this.commentAssembler = commentAssembler;
        this.clock = clock;
    }

    /** Idempotent; the like count only moves when a like is actually added. */
    @Transactional
    public void like(UUID viewerId, UUID memoryId) {
        memoryService.requireVisible(viewerId, memoryId);
        if (likeRepository.insertIfAbsent(viewerId, memoryId) == 1) {
            counters.likeChanged(memoryId, 1);
        }
    }

    @Transactional
    public void unlike(UUID viewerId, UUID memoryId) {
        memoryService.requireVisible(viewerId, memoryId);
        if (likeRepository.deleteIfPresent(viewerId, memoryId) == 1) {
            counters.likeChanged(memoryId, -1);
        }
    }

    @Transactional
    public void save(UUID viewerId, UUID memoryId) {
        memoryService.requireVisible(viewerId, memoryId);
        saveRepository.insertIfAbsent(viewerId, memoryId);
    }

    /** Unsaving works even if the memory has since become hidden, so users can always clean up their list. */
    @Transactional
    public void unsave(UUID viewerId, UUID memoryId) {
        saveRepository.deleteIfPresent(viewerId, memoryId);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentDto> comments(UUID viewerId, UUID memoryId, CursorPageRequest page) {
        Memory memory = memoryService.requireVisible(viewerId, memoryId);
        UUID cursor = page.cursor() == null ? MIN_UUID : page.cursor();
        List<Comment> rows = commentRepository.findTopLevel(memoryId, viewerId, cursor, Limit.of(page.limit() + 1));
        CursorPage<Comment> topLevel = CursorPage.fromLookahead(rows, page.limit(), Comment::getId);
        return new CursorPage<>(commentAssembler.threads(viewerId, memory.getAuthorId(), topLevel.items()),
                topLevel.nextCursor());
    }

    @Transactional
    public CommentDto comment(UUID viewerId, UUID memoryId, CreateCommentRequest request) {
        Memory memory = memoryService.requireVisible(viewerId, memoryId);
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty() || body.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidRequestException("Comments must be between 1 and " + MAX_COMMENT_LENGTH + " characters");
        }
        if (request.parentId() != null) {
            Comment parent = commentRepository.findById(request.parentId())
                    .filter(candidate -> candidate.getMemoryId().equals(memoryId))
                    .orElseThrow(() -> new InvalidRequestException("parentId is not a comment on this memory"));
            if (parent.getParentId() != null) {
                throw new InvalidRequestException("Replies can only be made to top-level comments");
            }
            if (parent.isDeleted()) {
                throw new InvalidRequestException("You cannot reply to a deleted comment");
            }
        }
        Comment comment = commentRepository.saveAndFlush(new Comment(memoryId, viewerId, request.parentId(), body));
        counters.commentChanged(memoryId, 1);
        return commentAssembler.single(viewerId, memory.getAuthorId(), comment);
    }

    /** The comment's author or the memory's author may delete it; anyone else gets a 404. */
    @Transactional
    public void deleteComment(UUID viewerId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        Memory memory;
        try {
            memory = memoryService.requireVisible(viewerId, comment.getMemoryId());
        } catch (NotFoundException hidden) {
            throw new NotFoundException("Comment not found");
        }
        if (!comment.getAuthorId().equals(viewerId) && !memory.getAuthorId().equals(viewerId)) {
            throw new NotFoundException("Comment not found");
        }
        if (commentRepository.softDelete(commentId, clock.instant()) == 1) {
            counters.commentChanged(comment.getMemoryId(), -1);
        }
    }
}
