package com.keepsake.common.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long RAND_B_MASK = (1L << 62) - 1;
    private static long lastTimestamp = -1;
    private static int randA;
    private static long randB;

    private UuidV7() {
    }

    public static synchronized UUID generate() {
        long timestamp = System.currentTimeMillis();
        if (timestamp > lastTimestamp) {
            lastTimestamp = timestamp;
            randA = RANDOM.nextInt(1 << 12);
            randB = RANDOM.nextLong() & RAND_B_MASK;
        } else {
            timestamp = lastTimestamp;
            incrementRandomness();
        }

        long mostSignificantBits = (timestamp << 16) | (0x7L << 12) | randA;
        long leastSignificantBits = Long.MIN_VALUE | randB;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static void incrementRandomness() {
        if (randB < RAND_B_MASK) {
            randB++;
        } else if (randA < 0xFFF) {
            randA++;
            randB = 0;
        } else {
            lastTimestamp++;
            randA = RANDOM.nextInt(1 << 12);
            randB = RANDOM.nextLong() & RAND_B_MASK;
        }
    }
}
