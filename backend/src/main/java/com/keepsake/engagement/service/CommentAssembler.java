package com.keepsake.engagement.service;

import com.keepsake.engagement.dto.CommentDto;
import com.keepsake.engagement.entity.Comment;
import com.keepsake.engagement.repository.CommentRepository;
import com.keepsake.user.dto.UserSummaryDto;
import com.keepsake.user.entity.User;
import com.keepsake.user.repository.UserRepository;
import com.keepsake.user.service.UserSummaries;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Builds comment DTOs. Callers must already have checked the memory is visible to the viewer. */
@Component
public class CommentAssembler {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final UserSummaries userSummaries;

    public CommentAssembler(
            CommentRepository commentRepository,
            UserRepository userRepository,
            UserSummaries userSummaries
    ) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.userSummaries = userSummaries;
    }

    /** The latest two top-level comments per memory, oldest first, keyed by memory id. */
    public Map<UUID, List<CommentDto>> previews(UUID viewerId, Map<UUID, UUID> memoryAuthors) {
        List<Comment> comments = commentRepository.findPreviewComments(memoryAuthors.keySet());
        Map<UUID, UserSummaryDto> authors = authors(comments);
        return comments.stream().collect(Collectors.groupingBy(Comment::getMemoryId, Collectors.mapping(
                comment -> toDto(comment, viewerId, memoryAuthors.get(comment.getMemoryId()), authors, List.of()),
                Collectors.toList())));
    }

    /** Top-level comments with their live replies nested one level deep. */
    public List<CommentDto> threads(UUID viewerId, UUID memoryAuthorId, List<Comment> topLevel) {
        if (topLevel.isEmpty()) {
            return List.of();
        }
        List<Comment> replies = commentRepository.findLiveReplies(topLevel.stream().map(Comment::getId).toList());
        Map<UUID, UserSummaryDto> authors = authors(Stream.concat(topLevel.stream(), replies.stream()).toList());
        Map<UUID, List<CommentDto>> repliesByParent = replies.stream().collect(Collectors.groupingBy(
                Comment::getParentId,
                Collectors.mapping(reply -> toDto(reply, viewerId, memoryAuthorId, authors, List.of()),
                        Collectors.toList())));
        return topLevel.stream().map(comment -> toDto(comment, viewerId, memoryAuthorId, authors,
                repliesByParent.getOrDefault(comment.getId(), List.of()))).toList();
    }

    public CommentDto single(UUID viewerId, UUID memoryAuthorId, Comment comment) {
        return toDto(comment, viewerId, memoryAuthorId, authors(List.of(comment)), List.of());
    }

    private Map<UUID, UserSummaryDto> authors(Collection<Comment> comments) {
        return userRepository.findAllById(comments.stream().map(Comment::getAuthorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, userSummaries::of));
    }

    /** A deleted comment keeps its place in the thread but reveals neither its author nor its text. */
    private static CommentDto toDto(
            Comment comment,
            UUID viewerId,
            UUID memoryAuthorId,
            Map<UUID, UserSummaryDto> authors,
            List<CommentDto> replies
    ) {
        boolean deleted = comment.isDeleted();
        return new CommentDto(
                comment.getId(),
                comment.getParentId(),
                deleted ? null : authors.get(comment.getAuthorId()),
                deleted ? null : comment.getBody(),
                deleted,
                !deleted && (comment.getAuthorId().equals(viewerId) || memoryAuthorId.equals(viewerId)),
                comment.getCreatedAt(),
                replies);
    }
}
