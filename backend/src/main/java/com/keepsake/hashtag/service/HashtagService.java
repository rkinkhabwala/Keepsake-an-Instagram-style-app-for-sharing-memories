package com.keepsake.hashtag.service;

import com.keepsake.common.id.UuidV7;
import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.hashtag.repository.HashtagRepository;
import com.keepsake.memory.dto.MemoryGridItemDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryDtoAssembler;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HashtagService {

    private final HashtagRepository hashtagRepository;
    private final MemoryDtoAssembler assembler;

    public HashtagService(HashtagRepository hashtagRepository, MemoryDtoAssembler assembler) {
        this.hashtagRepository = hashtagRepository;
        this.assembler = assembler;
    }

    /** Replaces a memory's hashtags with those in its caption. Runs inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void indexCaption(UUID memoryId, String caption) {
        List<String> tags = Hashtags.parse(caption);
        hashtagRepository.deleteForMemory(memoryId);
        if (tags.isEmpty()) {
            return;
        }
        tags.forEach(tag -> hashtagRepository.insertTagIfAbsent(UuidV7.generate(), tag));
        hashtagRepository.linkTags(memoryId, tags);
    }

    /** A tag's memories as grid tiles; an unused tag simply has none. */
    @Transactional(readOnly = true)
    public CursorPage<MemoryGridItemDto> memories(UUID viewerId, String rawTag, CursorPageRequest page) {
        String tag = Hashtags.normalizeQuery(rawTag)
                .orElseThrow(() -> new InvalidRequestException("Not a valid hashtag"));
        List<Memory> rows = hashtagRepository.findIdByTag(tag)
                .map(id -> hashtagRepository.findMemoriesVisibleTo(id, viewerId, page.cursorOrMax(), page.limit() + 1))
                .orElse(List.of());
        CursorPage<Memory> memories = CursorPage.fromLookahead(rows, page.limit(), Memory::getId);
        return new CursorPage<>(assembler.toGridItems(memories.items()), memories.nextCursor());
    }
}
