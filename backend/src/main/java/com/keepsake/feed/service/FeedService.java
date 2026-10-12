package com.keepsake.feed.service;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryDtoAssembler;
import com.keepsake.memory.service.MemoryListService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The home feed (spec §7): the viewer's fanned-out ids from {@link FeedStore}, merged with the memories of
 * followed celebrity accounts, newest first, cursor = last memory id.
 *
 * <p>Every candidate is re-checked in one query through {@code MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL} and
 * the feed scope (the viewer's own, or an author they follow now), so unfollows, blocks, close-friend and
 * visibility changes, and deletions apply on the next request even to ids fanned out earlier. Past the oldest
 * id a full feed keeps, and whenever Redis is unavailable, pages come from the fan-out-on-read query.
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final UUID MAX_UUID = new UUID(-1L, -1L);
    private static final int MIN_BATCH = 40;

    private final FeedStore store;
    private final CelebrityAccounts celebrities;
    private final MemoryRepository memoryRepository;
    private final MemoryDtoAssembler assembler;
    private final MemoryListService fanOutOnRead;

    public FeedService(
            FeedStore store,
            CelebrityAccounts celebrities,
            MemoryRepository memoryRepository,
            MemoryDtoAssembler assembler,
            MemoryListService fanOutOnRead
    ) {
        this.store = store;
        this.celebrities = celebrities;
        this.memoryRepository = memoryRepository;
        this.assembler = assembler;
        this.fanOutOnRead = fanOutOnRead;
    }

    /**
     * Deliberately not one transaction: each query borrows a connection only while it runs, instead of holding
     * one across the Redis calls and URL signing.
     */
    public CursorPage<MemoryDto> feed(UUID viewerId, CursorPageRequest page) {
        List<Memory> rows;
        try {
            rows = fromStore(viewerId, page.cursor(), page.limit() + 1);
        } catch (FeedStore.UnavailableException unavailable) {
            log.warn("Feed store unavailable; reading the feed from the database", unavailable);
            return fanOutOnRead.feed(viewerId, page);
        }
        CursorPage<Memory> memories = CursorPage.fromLookahead(rows, page.limit(), Memory::getId);
        return new CursorPage<>(assembler.toDtos(viewerId, memories.items()), memories.nextCursor());
    }

    private List<Memory> fromStore(UUID viewerId, UUID cursor, int want) {
        if (!store.touch(viewerId)) {
            store.create(viewerId);
            store.addAll(viewerId, memoryRepository.findFeedIds(viewerId, store.capacity()));
        }
        List<Memory> out = new ArrayList<>(want);
        String below = cursor == null ? null : cursor.toString();
        while (out.size() < want) {
            int batch = Math.max(2 * want, MIN_BATCH);
            List<String> ids = store.page(viewerId, below, batch);
            boolean exhausted = ids.size() < batch;
            // A full feed has dropped its oldest ids, so below its last one the database takes over.
            boolean continuesInDatabase = exhausted && store.size(viewerId) >= store.capacity();
            if (continuesInDatabase && ids.isEmpty()) {
                out.addAll(memoryRepository.findFeed(viewerId, below == null ? MAX_UUID : UUID.fromString(below),
                        want - out.size()));
                break;
            }
            // Below the last id of a batch, the next batch may hold ids this one has not seen.
            String floor = exhausted && !continuesInDatabase ? null : ids.getLast();

            Map<String, Memory> candidates = new TreeMap<>(Comparator.reverseOrder());
            visible(viewerId, ids).forEach(memory -> candidates.put(memory.getId().toString(), memory));
            celebrityMemories(viewerId, below, want - out.size())
                    .forEach(memory -> candidates.putIfAbsent(memory.getId().toString(), memory));
            for (Map.Entry<String, Memory> candidate : candidates.entrySet()) {
                if (out.size() == want || (floor != null && candidate.getKey().compareTo(floor) < 0)) {
                    break;
                }
                out.add(candidate.getValue());
            }

            if (exhausted) {
                if (continuesInDatabase && out.size() < want) {
                    out.addAll(memoryRepository.findFeed(viewerId, UUID.fromString(floor), want - out.size()));
                }
                break;
            }
            below = floor;
        }
        return out;
    }

    private List<Memory> visible(UUID viewerId, Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return memoryRepository.findFeedCandidatesVisibleTo(viewerId, ids.stream().map(UUID::fromString).toList());
    }

    private List<Memory> celebrityMemories(UUID viewerId, String below, int limit) {
        List<UUID> authors = celebrities.all().stream().filter(id -> !id.equals(viewerId)).toList();
        if (authors.isEmpty()) {
            return List.of();
        }
        return memoryRepository.findFeedFromAuthors(viewerId, authors,
                below == null ? MAX_UUID : UUID.fromString(below), limit);
    }
}
