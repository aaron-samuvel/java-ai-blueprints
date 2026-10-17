package io.github.aaronsamuvel.blueprints.finops.redis;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** {@link RedisBudgetGateway} on Spring Data Redis: every operation is one Lua script, executed atomically by Redis. */
public class SpringDataRedisBudgetGateway implements RedisBudgetGateway {

    private static final String INIT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              redis.call('HSET', KEYS[1], 'limit', ARGV[2], 'reserved', 0, 'used', 0)
              redis.call('EXPIRE', KEYS[1], ARGV[3])
            end
            """;

    static final RedisScript<Long> RESERVE = new DefaultRedisScript<>(INIT + """
            local limit = tonumber(redis.call('HGET', KEYS[1], 'limit'))
            local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved'))
            local used = tonumber(redis.call('HGET', KEYS[1], 'used'))
            local want = tonumber(ARGV[1])
            if used + reserved + want > limit then
              return math.max(0, limit - used - reserved)
            end
            redis.call('HINCRBY', KEYS[1], 'reserved', want)
            redis.call('SET', KEYS[2], want, 'EX', ARGV[3])
            return -1
            """, Long.class);

    static final RedisScript<Long> SETTLE = new DefaultRedisScript<>("""
            local held = redis.call('GET', KEYS[1])
            if not held then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('HINCRBY', KEYS[2], 'reserved', -tonumber(held))
            redis.call('HINCRBY', KEYS[2], 'used', tonumber(ARGV[1]))
            return 1
            """, Long.class);

    @SuppressWarnings("rawtypes")
    static final RedisScript<List> SNAPSHOT = new DefaultRedisScript<>(INIT + """
            return {tonumber(redis.call('HGET', KEYS[1], 'limit')), tonumber(redis.call('HGET', KEYS[1], 'reserved')),
                    tonumber(redis.call('HGET', KEYS[1], 'used'))}
            """, List.class);

    static final RedisScript<Long> SET_LIMIT = new DefaultRedisScript<>(INIT + """
            redis.call('HSET', KEYS[1], 'limit', ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public SpringDataRedisBudgetGateway(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long reserve(String budgetKey, String reservationKey, long tokens, long defaultLimit, long ttlSeconds) {
        return redis.execute(RESERVE, List.of(budgetKey, reservationKey), Long.toString(tokens), Long.toString(defaultLimit), Long.toString(ttlSeconds));
    }

    @Override
    public boolean settle(String reservationKey, String budgetKey, long actual) {
        return redis.execute(SETTLE, List.of(reservationKey, budgetKey), Long.toString(actual)) == 1L;
    }

    @Override
    public long[] snapshot(String budgetKey, long defaultLimit, long ttlSeconds) {
        List<?> r = redis.execute(SNAPSHOT, List.of(budgetKey), "0", Long.toString(defaultLimit), Long.toString(ttlSeconds));
        return new long[] {((Number) r.get(0)).longValue(), ((Number) r.get(1)).longValue(), ((Number) r.get(2)).longValue()};
    }

    @Override
    public void setLimit(String budgetKey, long limit, long ttlSeconds) {
        // INIT reads ARGV[2]/ARGV[3]; pass the new limit as the default too
        redis.execute(SET_LIMIT, List.of(budgetKey), Long.toString(limit), Long.toString(limit), Long.toString(ttlSeconds));
    }
}
