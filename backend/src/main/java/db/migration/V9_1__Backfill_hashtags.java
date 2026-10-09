package db.migration;

import com.keepsake.common.id.UuidV7;
import com.keepsake.hashtag.service.Hashtags;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Indexes hashtags in captions written before hashtags existed, with the same parser the app uses. */
public class V9_1__Backfill_hashtags extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement captions = connection.prepareStatement(
                     "SELECT id, caption FROM memories WHERE caption LIKE '%#%'");
             PreparedStatement insertTag = connection.prepareStatement(
                     "INSERT INTO hashtags (id, tag, created_at, updated_at) VALUES (?, ?, ?, ?) "
                             + "ON CONFLICT (tag) DO NOTHING");
             PreparedStatement link = connection.prepareStatement(
                     "INSERT INTO memory_hashtags (memory_id, hashtag_id, created_at, updated_at) "
                             + "SELECT ?, id, ?, ? FROM hashtags WHERE tag = ? ON CONFLICT DO NOTHING");
             ResultSet rows = captions.executeQuery()) {
            while (rows.next()) {
                UUID memoryId = rows.getObject("id", UUID.class);
                for (String tag : Hashtags.parse(rows.getString("caption"))) {
                    insertTag.setObject(1, UuidV7.generate());
                    insertTag.setString(2, tag);
                    insertTag.setTimestamp(3, now);
                    insertTag.setTimestamp(4, now);
                    insertTag.executeUpdate();
                    link.setObject(1, memoryId);
                    link.setTimestamp(2, now);
                    link.setTimestamp(3, now);
                    link.setString(4, tag);
                    link.executeUpdate();
                }
            }
        }
    }
}
