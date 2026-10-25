package io.github.frankie7techy.haraka.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.frankie7techy.haraka.common.exception.IdempotencyKeyException;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * One reusable wrapper that gives every money-mutating endpoint identical idempotency
 * semantics. Controllers declare (endpoint id, caller, key, canonical payload, success
 * status, response type, operation); this class handles validation, reservation, replay
 * and failure-release in exactly the same way for all of them.
 */
@Component
public class IdempotentRequests {

    private final IdempotencyService idempotencyService;
    private final IdempotencyHasher hasher;
    private final ObjectMapper mapper;

    public IdempotentRequests(IdempotencyService idempotencyService, IdempotencyHasher hasher, ObjectMapper mapper) {
        this.idempotencyService = idempotencyService;
        this.hasher = hasher;
        this.mapper = mapper;
    }

    public <T> ResponseEntity<T> execute(String endpoint, UserPrincipal caller, String idempotencyKey,
                                         Object canonicalPayload, int successStatus, Class<T> responseType,
                                         Supplier<T> operation) {
        if (!hasher.isKeyValid(idempotencyKey)) {
            throw new IdempotencyKeyException(
                    "Idempotency-Key header is required and must match ^[A-Za-z0-9_-]{16,64}$");
        }
        String requestHash = hasher.hash(endpoint, toJson(canonicalPayload));

        IdempotencyService.Reservation reservation =
                idempotencyService.reserve(caller.userId(), idempotencyKey, endpoint, requestHash);
        if (reservation instanceof IdempotencyService.Reservation.Replay replay) {
            IdempotencyService.StoredResponse stored = idempotencyService.replayed(replay);
            return ResponseEntity.status(stored.statusCode()).body(fromJson(stored.jsonBody(), responseType));
        }

        IdempotencyRecord record = ((IdempotencyService.Reservation.Start) reservation).record();
        try {
            T result = operation.get();
            String json = toJson(result);
            idempotencyService.complete(record.getId(), successStatus, json);
            return ResponseEntity.status(successStatus).body(result);
        } catch (RuntimeException failure) {
            idempotencyService.release(record.getId());
            throw failure;
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise idempotent request payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not deserialise stored idempotent response", e);
        }
    }
}
