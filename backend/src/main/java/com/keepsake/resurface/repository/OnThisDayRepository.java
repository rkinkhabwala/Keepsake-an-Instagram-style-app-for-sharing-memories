package com.keepsake.resurface.repository;

import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryAccessPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface OnThisDayRepository extends Repository<Memory, UUID> {

    /**
     * The viewer's own memories and memories they are tagged in, from years before {@code beforeYear}, that
     * happened on one of the given month/day pairs (the second is used only for the Feb 29 rule; pass the first
     * twice otherwise). Tagged memories still go through the visibility rules.
     */
    @Query(value = """
            SELECT m.* FROM memories m JOIN users a ON a.id = m.author_id
            WHERE (m.author_id = :viewerId
                   OR EXISTS (SELECT 1 FROM memory_people p WHERE p.memory_id = m.id AND p.user_id = :viewerId))
              AND EXTRACT(YEAR FROM m.occurred_on) < :beforeYear
              AND ((EXTRACT(MONTH FROM m.occurred_on) = :month1 AND EXTRACT(DAY FROM m.occurred_on) = :day1)
                OR (EXTRACT(MONTH FROM m.occurred_on) = :month2 AND EXTRACT(DAY FROM m.occurred_on) = :day2))
              AND """ + MemoryAccessPolicy.VISIBLE_TO_VIEWER_SQL + """
            ORDER BY m.occurred_on DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findOnDays(UUID viewerId, int beforeYear, int month1, int day1, int month2, int day2, int limit);
}
