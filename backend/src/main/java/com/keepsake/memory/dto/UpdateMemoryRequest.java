package com.keepsake.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.memory.entity.Visibility;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * PATCH semantics: an absent field is left unchanged; {@code "caption": null} and {@code "location": null} clear
 * those values. {@code occurredOn} and {@code visibility} are required columns and cannot be cleared.
 */
public record UpdateMemoryRequest(
        boolean captionPresent,
        String caption,
        LocalDate occurredOn,
        boolean locationPresent,
        LocationDto location,
        Visibility visibility,
        List<String> taggedUsernames
) {

    public static UpdateMemoryRequest from(JsonNode body, ObjectMapper mapper) {
        if (body == null || !body.isObject()) {
            throw new InvalidRequestException("Request body must be a JSON object");
        }
        return new UpdateMemoryRequest(
                body.has("caption"),
                read(body, "caption", String.class, mapper),
                required(body, "occurredOn", LocalDate.class, mapper),
                body.has("location"),
                read(body, "location", LocationDto.class, mapper),
                required(body, "visibility", Visibility.class, mapper),
                body.has("taggedUsernames")
                        ? Arrays.asList(required(body, "taggedUsernames", String[].class, mapper))
                        : null);
    }

    private static <T> T required(JsonNode body, String field, Class<T> type, ObjectMapper mapper) {
        if (body.has(field) && body.get(field).isNull()) {
            throw new InvalidRequestException(field + " cannot be null");
        }
        return read(body, field, type, mapper);
    }

    private static <T> T read(JsonNode body, String field, Class<T> type, ObjectMapper mapper) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(value, type);
        } catch (Exception exception) {
            throw new InvalidRequestException(field + " is invalid");
        }
    }
}
