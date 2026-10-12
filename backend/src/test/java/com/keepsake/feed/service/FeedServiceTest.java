package com.keepsake.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.keepsake.common.pagination.CursorPage;
import com.keepsake.common.pagination.CursorPageRequest;
import com.keepsake.memory.dto.MemoryDto;
import com.keepsake.memory.repository.MemoryRepository;
import com.keepsake.memory.service.MemoryDtoAssembler;
import com.keepsake.memory.service.MemoryListService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class FeedServiceTest {

    @Test
    void readsFromTheDatabaseWhenRedisIsDown() {
        FeedStore store = mock(FeedStore.class);
        MemoryListService fanOutOnRead = mock(MemoryListService.class);
        UUID viewerId = UUID.randomUUID();
        CursorPageRequest page = CursorPageRequest.parse(null, 20);
        CursorPage<MemoryDto> fromDatabase = new CursorPage<>(List.of(), null);
        when(store.touch(viewerId)).thenThrow(
                new FeedStore.UnavailableException(new RedisConnectionFailureException("down")));
        when(fanOutOnRead.feed(viewerId, page)).thenReturn(fromDatabase);
        FeedService service = new FeedService(store, mock(CelebrityAccounts.class), mock(MemoryRepository.class),
                mock(MemoryDtoAssembler.class), fanOutOnRead);

        assertThat(service.feed(viewerId, page)).isSameAs(fromDatabase);
    }
}
