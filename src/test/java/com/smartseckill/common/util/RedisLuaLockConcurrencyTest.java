package com.smartseckill.common.util;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis+Lua 锁高并发压测（纯 Lettuce 直连，不依赖 Spring Boot 上下文）。
 *
 * <p>每个测试场景都直接执行 Lua 脚本，100% 还原真实 Redis 原子语义。
 * 所有测试需要本地 Redis 运行在 localhost:6379。</p>
 *
 * <p>测试涵盖 6 个高并发场景：</p>
 * <ol>
 *   <li>单用户 500 并发同一 Token → 仅 1 次成功</li>
 *   <li>单用户 200 并发不同 Token → 仅 1 次成功，其余 ALREADY_LOCKED</li>
 *   <li>300 人抢 100 件库存 → 恰好 100 人成功，不超卖</li>
 *   <li>库存耗尽后持续压测 → 全部 SOLD_OUT</li>
 *   <li>锁释放→再加锁竞速循环</li>
 *   <li>1000 人抢 500 件极限压测</li>
 * </ol>
 */
@DisplayName("Redis+Lua Lock High-Concurrency Stress Tests")
class RedisLuaLockConcurrencyTest {

    // ==================== Lua 脚本（与 SeckillLockUtil 完全一致） ====================

    private static final String ACQUIRE_LOCK_LUA =
            // Step 1: validate & consume anti-duplicate token
            "local tokenKey = KEYS[1] " +
            "local expectedToken = ARGV[1] " +
            "local actualToken = redis.call('GET', tokenKey) " +
            "if actualToken ~= expectedToken then " +
            "    return 0 " +  // TOKEN_INVALID
            "end " +
            "redis.call('DEL', tokenKey) " +

            // Step 1.5: check completed marker (prevent re-purchase after lock release)
            "local completedKey = KEYS[4] " +
            "if redis.call('EXISTS', completedKey) == 1 then " +
            "    return -4 " +  // ALREADY_COMPLETED
            "end " +

            // Step 2: check for existing lock (prevent duplicate submission)
            "local lockKey = KEYS[2] " +
            "local existingLock = redis.call('EXISTS', lockKey) " +
            "if existingLock == 1 then " +
            "    return -1 " +  // ALREADY_LOCKED
            "end " +

            // Step 3: check & atomically decrement stock
            "local stockKey = KEYS[3] " +
            "local stock = redis.call('GET', stockKey) " +
            "if stock == false then " +
            "    return -3 " +  // NO_STOCK_KEY
            "end " +
            "local stockNum = tonumber(stock) " +
            "if stockNum == nil or stockNum <= 0 then " +
            "    return -2 " +  // SOLD_OUT
            "end " +
            "redis.call('DECR', stockKey) " +

            // Step 4: acquire lock (with fallback TTL to prevent deadlock)
            "local userId = ARGV[2] " +
            "local lockTTL = tonumber(ARGV[3]) " +
            "redis.call('SETEX', lockKey, lockTTL, userId) " +

            "return 1";

    private static final String RELEASE_LOCK_LUA =
            "local lockKey = KEYS[1] " +
            "local expectedUserId = ARGV[1] " +
            "local lockValue = redis.call('GET', lockKey) " +
            "if lockValue == expectedUserId then " +
            "    redis.call('DEL', lockKey) " +
            "    return 1 " +
            "end " +
            "return 0";

    // ==================== Key prefixes (matching production constants) ====================

    private static final String TOKEN_PREFIX = "seckill:token:";
    private static final String LOCK_PREFIX  = "seckill:lock:";
    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String COMPLETED_PREFIX = "seckill:completed:";

    // ==================== Return codes ====================

    private static final long ACQUIRE_SUCCESS         = 1;
    private static final long ACQUIRE_TOKEN_INVALID   = 0;
    private static final long ACQUIRE_ALREADY_LOCKED  = -1;
    private static final long ACQUIRE_SOLD_OUT        = -2;
    private static final long ACQUIRE_NO_STOCK_KEY    = -3;
    private static final long ACQUIRE_ALREADY_COMPLETED = -4;

    // ==================== Shared Redis resources ====================

    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;
    private static RedisCommands<String, String> redis;

    private static final Long ACTIVITY_ID = 8888L;
    private static final int  THREADS = Math.min(Runtime.getRuntime().availableProcessors() * 2, 64);

    @BeforeAll
    static void connectRedis() {
        redisClient = RedisClient.create(RedisURI.create("redis://localhost:6379/0"));
        connection = redisClient.connect();
        redis = connection.sync();
    }

    @AfterAll
    static void disconnectRedis() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @BeforeEach
    void cleanUp() {
        // Clean stock key
        redis.del(STOCK_PREFIX + ACTIVITY_ID);
        // Clean lock keys and completed keys for users 1..7000
        String[] keys = new String[400];
        int idx = 0;
        for (long uid = 1; uid <= 7000 && idx < 400; uid++) {
            keys[idx++] = LOCK_PREFIX + ACTIVITY_ID + ":" + uid;
            keys[idx++] = COMPLETED_PREFIX + ACTIVITY_ID + ":" + uid;
        }
        redis.del(keys);
    }

    // ==================== Helpers ====================

    private String generateToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.setex(TOKEN_PREFIX + userId + ":" + ACTIVITY_ID, 300, token);
        return token;
    }

    private long acquireLock(long userId, String token) {
        Long result = redis.eval(ACQUIRE_LOCK_LUA,
                io.lettuce.core.ScriptOutputType.INTEGER,
                new String[]{
                        TOKEN_PREFIX + userId + ":" + ACTIVITY_ID,
                        LOCK_PREFIX + ACTIVITY_ID + ":" + userId,
                        STOCK_PREFIX + ACTIVITY_ID,
                        COMPLETED_PREFIX + ACTIVITY_ID + ":" + userId
                },
                token,
                String.valueOf(userId),
                "60",  // LOCK_TTL_SECONDS
                "7200" // COMPLETED_TTL_SECONDS
        );
        return result != null ? result : ACQUIRE_TOKEN_INVALID;
    }

    private boolean releaseLock(long userId) {
        Long result = redis.eval(RELEASE_LOCK_LUA,
                io.lettuce.core.ScriptOutputType.INTEGER,
                new String[]{LOCK_PREFIX + ACTIVITY_ID + ":" + userId},
                String.valueOf(userId)
        );
        return result != null && result == 1L;
    }

    private boolean isLocked(long userId) {
        return redis.exists(LOCK_PREFIX + ACTIVITY_ID + ":" + userId) == 1;
    }

    // ================================================================
    // 场景1：单用户 500 并发同一 Token → 仅 1 次成功
    // ================================================================

    @Nested
    @DisplayName("Scenario 1: 500 threads, same user, same Token")
    class SingleUserSameToken {

        @Test
        @DisplayName("Exactly 1 success out of 500 concurrent identical requests")
        void exactlyOneSuccess() throws Exception {
            final int CONCURRENCY = 500;
            final long USER_ID = 1001L;

            // Stock = 1
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "1");
            String token = generateToken(USER_ID);

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENCY);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger tokenInvalid = new AtomicInteger(0);
            AtomicInteger alreadyLocked = new AtomicInteger(0);
            AtomicInteger soldOut = new AtomicInteger(0);

            for (int i = 0; i < CONCURRENCY; i++) {
                pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(USER_ID, token);
                    if (r == ACQUIRE_SUCCESS) successCount.incrementAndGet();
                    else if (r == ACQUIRE_TOKEN_INVALID) tokenInvalid.incrementAndGet();
                    else if (r == ACQUIRE_ALREADY_LOCKED) alreadyLocked.incrementAndGet();
                    else if (r == ACQUIRE_SOLD_OUT) soldOut.incrementAndGet();
                    done.countDown();
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "All threads should complete");

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(1, successCount.get(),
                    "Exactly 1 of " + CONCURRENCY + " concurrent requests must succeed");
            assertEquals(CONCURRENCY - 1,
                    tokenInvalid.get() + alreadyLocked.get() + soldOut.get(),
                    "All other " + (CONCURRENCY - 1) + " must be rejected");

            // Stock = 0 after the single success
            assertEquals("0", redis.get(STOCK_PREFIX + ACTIVITY_ID));
            assertTrue(isLocked(USER_ID));

            releaseLock(USER_ID);
        }
    }

    // ================================================================
    // 场景2：单用户 200 并发不同 Token → 仅 1 次成功
    // ================================================================

    @Nested
    @DisplayName("Scenario 2: 200 threads, same user, different tokens each")
    class SingleUserDifferentTokens {

        @Test
        @DisplayName("Exactly 1 success, rest ALREADY_LOCKED")
        void exactlyOneSuccessRestAlreadyLocked() throws Exception {
            final int CONCURRENCY = 200;
            final long USER_ID = 2001L;

            // Stock = 10 (plenty)
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "10");

            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
                tokens.add(generateToken(USER_ID));
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENCY);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyLocked = new AtomicInteger(0);
            AtomicInteger tokenInvalid = new AtomicInteger(0);

            for (int i = 0; i < CONCURRENCY; i++) {
                final String t = tokens.get(i);
                pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(USER_ID, t);
                    if (r == ACQUIRE_SUCCESS) successCount.incrementAndGet();
                    else if (r == ACQUIRE_ALREADY_LOCKED) alreadyLocked.incrementAndGet();
                    else if (r == ACQUIRE_TOKEN_INVALID) tokenInvalid.incrementAndGet();
                    done.countDown();
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(1, successCount.get(),
                    "Only 1 of " + CONCURRENCY + " threads should succeed");
            assertEquals(CONCURRENCY - 1, alreadyLocked.get() + tokenInvalid.get(),
                    "All remaining threads must be rejected");

            // Stock only decreased by 1
            assertEquals("9", redis.get(STOCK_PREFIX + ACTIVITY_ID));

            releaseLock(USER_ID);
        }
    }

    // ================================================================
    // 场景3：N 用户抢 M 件库存 → 精确去重，不超卖
    // ================================================================

    @Nested
    @DisplayName("Scenario 3: Multi-user stock race — no oversell, no duplicates")
    class MultiUserStockRace {

        @Test
        @DisplayName("300 users × 100 stock → exactly 100 wins, stock=0")
        void exactly100WinsOutOf300Users() throws Exception {
            final int USERS = 300;
            final int STOCK = 100;
            final long UID_BASE = 3001L;

            redis.set(STOCK_PREFIX + ACTIVITY_ID, String.valueOf(STOCK));

            // Pre-generate all tokens
            long[] uids = new long[USERS];
            String[] tokens = new String[USERS];
            for (int i = 0; i < USERS; i++) {
                uids[i] = UID_BASE + i;
                tokens[i] = generateToken(uids[i]);
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(USERS);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);
            AtomicInteger otherFail = new AtomicInteger(0);

            for (int i = 0; i < USERS; i++) {
                final long uid = uids[i];
                final String tok = tokens[i];
                pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(uid, tok);
                    if (r == ACQUIRE_SUCCESS) successCount.incrementAndGet();
                    else if (r == ACQUIRE_SOLD_OUT) soldOutCount.incrementAndGet();
                    else otherFail.incrementAndGet();
                    done.countDown();
                });
            }

            long t0 = System.currentTimeMillis();
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS));
            long elapsed = System.currentTimeMillis() - t0;

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            // === Core assertions ===
            assertEquals(STOCK, successCount.get(),
                    "Exactly " + STOCK + " users must succeed (no oversell)");
            assertEquals(USERS - STOCK, soldOutCount.get(),
                    "Exactly " + (USERS - STOCK) + " users must be rejected as sold-out");
            assertEquals(0, otherFail.get(),
                    "No unexpected failures (no token invalid / no lock conflicts)");
            assertEquals("0", redis.get(STOCK_PREFIX + ACTIVITY_ID),
                    "Redis stock must be exactly 0");

            System.out.printf("[300×100 Race] %d wins / %d total in %d ms (%.0f req/s)%n",
                    successCount.get(), USERS, elapsed,
                    USERS * 1000.0 / Math.max(elapsed, 1));

            // Cleanup locks
            for (long uid : uids) { releaseLock(uid); }
        }
    }

    // ================================================================
    // 场景4：库存耗尽后继续压测 → 全部 SOLD_OUT
    // ================================================================

    @Nested
    @DisplayName("Scenario 4: All rejected after stock exhausted")
    class SoldOutAfterDrain {

        @Test
        @DisplayName("Wave 1: 100 users × 10 stock → 10 wins; Wave 2: 100 users → all SOLD_OUT")
        void allRejectedAfterExhaustion() throws Exception {
            final int STOCK = 10;
            final int WAVE = 100;
            final long UID_BASE = 4001L;

            redis.set(STOCK_PREFIX + ACTIVITY_ID, String.valueOf(STOCK));
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);

            // === Wave 1: 100 users, 10 stock ===
            AtomicInteger w1Success = new AtomicInteger();
            AtomicInteger w1SoldOut = new AtomicInteger();
            CountDownLatch w1Start = new CountDownLatch(1);
            CountDownLatch w1Done = new CountDownLatch(WAVE);

            for (int i = 0; i < WAVE; i++) {
                long uid = UID_BASE + i;
                String tok = generateToken(uid);
                pool.submit(() -> {
                    try { w1Start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(uid, tok);
                    if (r == ACQUIRE_SUCCESS) w1Success.incrementAndGet();
                    else if (r == ACQUIRE_SOLD_OUT) w1SoldOut.incrementAndGet();
                    w1Done.countDown();
                });
            }

            w1Start.countDown();
            w1Done.await(30, TimeUnit.SECONDS);

            assertEquals(STOCK, w1Success.get());
            assertEquals(WAVE - STOCK, w1SoldOut.get());
            assertEquals("0", redis.get(STOCK_PREFIX + ACTIVITY_ID));

            // Clean wave 1 locks
            for (int i = 0; i < WAVE; i++) releaseLock(UID_BASE + i);

            // === Wave 2: 100 users, stock=0 ===
            AtomicInteger w2Success = new AtomicInteger();
            AtomicInteger w2SoldOut = new AtomicInteger();
            CountDownLatch w2Start = new CountDownLatch(1);
            CountDownLatch w2Done = new CountDownLatch(WAVE);

            for (int i = WAVE; i < WAVE * 2; i++) {
                long uid = UID_BASE + i;
                String tok = generateToken(uid);
                pool.submit(() -> {
                    try { w2Start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(uid, tok);
                    if (r == ACQUIRE_SUCCESS) w2Success.incrementAndGet();
                    else if (r == ACQUIRE_SOLD_OUT) w2SoldOut.incrementAndGet();
                    w2Done.countDown();
                });
            }

            w2Start.countDown();
            w2Done.await(30, TimeUnit.SECONDS);

            assertEquals(0, w2Success.get(), "Wave 2: 0 successes (stock already 0)");
            assertEquals(WAVE, w2SoldOut.get(), "Wave 2: all " + WAVE + " must be SOLD_OUT");

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ================================================================
    // 场景5：锁释放→再加锁竞速循环
    // ================================================================

    @Nested
    @DisplayName("Scenario 5: Rapid acquire-release cycling")
    class LockReleaseReacquire {

        @Test
        @DisplayName("Release immediately frees lock for next user")
        void releaseFreesLockForNextUser() {
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "5");

            long rA = acquireLock(5001L, generateToken(5001L));
            assertEquals(ACQUIRE_SUCCESS, rA);
            assertTrue(releaseLock(5001L));

            long rB = acquireLock(5002L, generateToken(5002L));
            assertEquals(ACQUIRE_SUCCESS, rB,
                    "User B must acquire lock immediately after A releases");
            assertTrue(releaseLock(5002L));
        }

        @Test
        @DisplayName("20 threads × 50 acquire-release cycles → 100% success rate")
        void rapidAcquireReleaseAllSuccess() throws Exception {
            final int CYCLES = 50;
            final int THREAD_COUNT = 20;
            final long UID_BASE = 5501L;

            redis.set(STOCK_PREFIX + ACTIVITY_ID, String.valueOf(CYCLES * THREAD_COUNT));

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);

            AtomicInteger totalSuccess = new AtomicInteger();
            AtomicInteger totalFail = new AtomicInteger();
            List<String> errors = Collections.synchronizedList(new ArrayList<>());

            for (int t = 0; t < THREAD_COUNT; t++) {
                final long uid = UID_BASE + t;
                pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { return; }
                    try {
                        for (int c = 0; c < CYCLES; c++) {
                            long r = acquireLock(uid, generateToken(uid));
                            if (r == ACQUIRE_SUCCESS) {
                                totalSuccess.incrementAndGet();
                            } else {
                                totalFail.incrementAndGet();
                                errors.add("uid=" + uid + " cycle=" + c + " result=" + r);
                            }
                            Thread.sleep(1); // simulate order creation
                            releaseLock(uid);
                        }
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS));

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            assertTrue(errors.isEmpty(), "All cycles should succeed: " + errors);
            assertEquals(CYCLES * THREAD_COUNT, totalSuccess.get());
            assertEquals(0, totalFail.get());
        }
    }

    // ================================================================
    // 场景6：千人千面极限压测 — 1000 人抢 500 件
    // ================================================================

    @Nested
    @DisplayName("Scenario 6: EXTREME — 1000 users × 500 stock")
    class ExtremeConcurrency {

        @Test
        @DisplayName("1000 users competing for 500 items → exactly 500 wins, stock=0")
        void thousandUsers500Stock() throws Exception {
            final int USERS = 1000;
            final int STOCK = 500;
            final long UID_BASE = 6001L;

            redis.set(STOCK_PREFIX + ACTIVITY_ID, String.valueOf(STOCK));

            long[] uids = new long[USERS];
            String[] tokens = new String[USERS];
            for (int i = 0; i < USERS; i++) {
                uids[i] = UID_BASE + i;
                tokens[i] = generateToken(uids[i]);
            }

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(USERS);

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger soldOutCount = new AtomicInteger();

            for (int i = 0; i < USERS; i++) {
                final long uid = uids[i];
                final String tok = tokens[i];
                pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { return; }
                    long r = acquireLock(uid, tok);
                    if (r == ACQUIRE_SUCCESS) successCount.incrementAndGet();
                    else if (r == ACQUIRE_SOLD_OUT) soldOutCount.incrementAndGet();
                    done.countDown();
                });
            }

            long t0 = System.currentTimeMillis();
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "All 1000 threads must complete");
            long elapsed = System.currentTimeMillis() - t0;

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            // === THE ULTIMATE ASSERTIONS ===
            assertEquals(STOCK, successCount.get(),
                    "EXACTLY " + STOCK + " of " + USERS + " must succeed — " +
                    "even 1 off means the Lua script has a race condition");
            assertEquals(USERS - STOCK, soldOutCount.get(),
                    "Exactly " + (USERS - STOCK) + " must be rejected");
            assertEquals("0", redis.get(STOCK_PREFIX + ACTIVITY_ID),
                    "Stock must hit exactly 0 — not -1, not +1");

            double tps = USERS * 1000.0 / Math.max(elapsed, 1);
            System.out.printf("[1000×500 EXTREME] %d wins / %d total in %d ms (%.0f req/s, %.0f lock-ops/s)%n",
                    successCount.get(), USERS, elapsed, tps,
                    successCount.get() * 1000.0 / Math.max(elapsed, 1));

            // Cleanup
            for (long uid : uids) releaseLock(uid);
        }
    }

    // ================================================================
    // 场景7：Lua 脚本返回值正确性（Smoke test）
    // ================================================================

    @Nested
    @DisplayName("Scenario 7: Return-code correctness checks")
    class ReturnCodes {

        @Test
        @DisplayName("Null/empty token → 0 (TOKEN_INVALID)")
        void nullTokenReturnsTokenInvalid() {
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "5");
            assertEquals(ACQUIRE_TOKEN_INVALID, acquireLock(7001L, ""));
            assertEquals(ACQUIRE_TOKEN_INVALID, acquireLock(7001L, "fake-token"));
        }

        @Test
        @DisplayName("Zero stock → -2 (SOLD_OUT)")
        void zeroStockReturnsSoldOut() {
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "0");
            long uid = 7002L;
            assertEquals(ACQUIRE_SOLD_OUT, acquireLock(uid, generateToken(uid)));
            assertFalse(isLocked(uid), "No lock should be held when SOLD_OUT");
        }

        @Test
        @DisplayName("No stock key → -3 (NO_STOCK_KEY)")
        void noStockKeyReturnsNoStockKey() {
            // stock key already deleted in @BeforeEach
            long uid = 7003L;
            assertEquals(ACQUIRE_NO_STOCK_KEY, acquireLock(uid, generateToken(uid)));
            assertFalse(isLocked(uid));
        }

        @Test
        @DisplayName("Cannot release another user's lock")
        void cannotReleaseAnotherUsersLock() {
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "2");
            assertEquals(ACQUIRE_SUCCESS, acquireLock(7101L, generateToken(7101L)));
            assertFalse(releaseLock(7102L), "User 7102 cannot release 7101's lock");
            assertTrue(isLocked(7101L));
            releaseLock(7101L);
        }

        @Test
        @DisplayName("Double release is idempotent")
        void doubleReleaseIsIdempotent() {
            redis.set(STOCK_PREFIX + ACTIVITY_ID, "1");
            long uid = 7103L;
            assertEquals(ACQUIRE_SUCCESS, acquireLock(uid, generateToken(uid)));
            assertTrue(releaseLock(uid));
            assertFalse(releaseLock(uid), "Second release on already-released lock returns false");
        }
    }
}
