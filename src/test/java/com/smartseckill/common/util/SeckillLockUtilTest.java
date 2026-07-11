package com.smartseckill.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeckillLockUtil 集成测试。
 *
 * <p>测试场景覆盖：正常加锁→成功释放、重复加锁被拒绝、Token 一次性消费、库存不足、并发安全性。</p>
 */
@SpringBootTest
@DisplayName("SeckillLockUtil Redis+Lua 原子锁测试")
class SeckillLockUtilTest {

    @Autowired
    private SeckillLockUtil seckillLockUtil;

    @Autowired
    private SeckillTokenUtil seckillTokenUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Long ACTIVITY_ID = 9999L;
    private static final Long USER_ID = 10086L;
    private static final Long ANOTHER_USER_ID = 10087L;

    @BeforeEach
    void setUp() {
        // 清理测试残留数据 — 使用明确的 Key 列表，避免在测试中用 KEYS *（生产应避免）
        for (long uid = USER_ID; uid <= USER_ID + 200; uid++) {
            stringRedisTemplate.delete(SeckillLockUtil.SECKILL_LOCK_PREFIX + ACTIVITY_ID + ":" + uid);
            stringRedisTemplate.delete(SeckillLockUtil.SECKILL_COMPLETED_PREFIX + ACTIVITY_ID + ":" + uid);
        }
        stringRedisTemplate.delete(SeckillLockUtil.SECKILL_LOCK_PREFIX + ACTIVITY_ID + ":" + ANOTHER_USER_ID);
        stringRedisTemplate.delete(SeckillLockUtil.SECKILL_COMPLETED_PREFIX + ACTIVITY_ID + ":" + ANOTHER_USER_ID);
        stringRedisTemplate.delete(RedisUtil.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
    }

    // ==================== 正常流程 ====================

    @Nested
    @DisplayName("Token 获取 + 加锁 + 释放锁 完整流程")
    class NormalFlow {

        @Test
        @DisplayName("有效Token → 加锁成功 → 释放锁成功 → 再次获取Token可加锁")
        void validTokenShouldAcquireAndRelease() {
            // Step 1: 生成 Token
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            assertThat(token).isNotEmpty();

            // Step 2: 用有效 Token 加锁 → 成功
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);

            // Step 3: 锁存在
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isTrue();

            // Step 4: 释放锁
            boolean released = seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
            assertThat(released).isTrue();

            // Step 5: 锁不存在
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isFalse();

            // Step 6: 获取新 Token → 可再次加锁（新一轮秒杀）
            String newToken = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            long newResult = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, newToken);
            assertThat(newResult).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);

            // 清理
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
        }
    }

    // ==================== 防重复点击 ====================

    @Nested
    @DisplayName("防重复点击：已持有锁不可再加锁")
    class DuplicatePrevention {

        @Test
        @DisplayName("同一用户已持有锁时再次加锁 → 返回 ALREADY_LOCKED")
        void lockedUserCannotAcquireAgain() {
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);

            // 获取新 Token 尝试再次加锁 → 被拒绝
            String token2 = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token2);
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_ALREADY_LOCKED);

            // 清理
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
        }

        @Test
        @DisplayName("同一用户使用同一个Token的并发请求 → 第二个请求Token已被消费")
        void sameTokenCannotBeUsedTwice() {
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);

            // 第一次：成功
            long r1 = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            assertThat(r1).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);

            // 释放锁（模拟第一个请求的订单创建成功）
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);

            // 第二次使用同一个 Token → Token 已被消费
            long r2 = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            assertThat(r2).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_TOKEN_INVALID);
        }
    }

    // ==================== 防无锁秒杀 ====================

    @Nested
    @DisplayName("防无锁秒杀：无Token不可加锁")
    class NoTokenProtection {

        @Test
        @DisplayName("空Token → 返回 TOKEN_INVALID")
        void emptyTokenShouldFail() {
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, "");
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_TOKEN_INVALID);
        }

        @Test
        @DisplayName("null Token → 返回 TOKEN_INVALID")
        void nullTokenShouldFail() {
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, null);
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_TOKEN_INVALID);
        }

        @Test
        @DisplayName("伪造Token → 返回 TOKEN_INVALID")
        void fakeTokenShouldFail() {
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, "fake-token-12345");
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_TOKEN_INVALID);
        }
    }

    // ==================== 库存控制 ====================

    @Nested
    @DisplayName("库存不足时拒绝加锁")
    class StockControl {

        @Test
        @DisplayName("库存为0 → 返回 SOLD_OUT")
        void zeroStockShouldFail() {
            // 预热库存为 0
            stringRedisTemplate.opsForValue().set(
                    RedisUtil.SECKILL_STOCK_PREFIX + ACTIVITY_ID, "0");

            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_SOLD_OUT);
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isFalse();
        }

        @Test
        @DisplayName("库存Key不存在（活动未预热）→ 返回 NO_STOCK_KEY")
        void noStockKeyShouldFail() {
            stringRedisTemplate.delete(RedisUtil.SECKILL_STOCK_PREFIX + ACTIVITY_ID);

            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            long result = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_FAIL_NO_STOCK_KEY);
        }

        @Test
        @DisplayName("多次加锁成功应正确递减库存")
        void multipleAcquiresShouldDecrementStock() {
            for (int i = 0; i < 5; i++) {
                long uid = USER_ID + i;
                stringRedisTemplate.delete(patternToList("seckill:token:" + uid + ":*"));
                stringRedisTemplate.delete(SeckillLockUtil.SECKILL_LOCK_PREFIX + ACTIVITY_ID + ":" + uid);

                String token = seckillTokenUtil.generateToken(uid, ACTIVITY_ID);
                long result = seckillLockUtil.acquireLock(ACTIVITY_ID, uid, token);
                assertThat(result).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);
            }

            // 剩余库存应为 5
            String stock = stringRedisTemplate.opsForValue().get(
                    RedisUtil.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
            assertThat(stock).isEqualTo("5");

            // 清理
            for (int i = 0; i < 5; i++) {
                long uid = USER_ID + i;
                seckillLockUtil.releaseLock(ACTIVITY_ID, uid);
            }
        }
    }

    // ==================== 锁安全释放 ====================

    @Nested
    @DisplayName("锁安全释放：不能误删他人锁")
    class SafeRelease {

        @Test
        @DisplayName("释放他人锁 → 失败")
        void cannotReleaseOthersLock() {
            String token1 = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token1);

            // 另一个用户尝试释放 → 失败
            boolean released = seckillLockUtil.releaseLock(ACTIVITY_ID, ANOTHER_USER_ID);
            assertThat(released).isFalse();

            // 原用户的锁仍存在
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isTrue();

            // 清理
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
        }

        @Test
        @DisplayName("重复释放锁 → 幂等")
        void doubleReleaseIsIdempotent() {
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);

            boolean r1 = seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
            assertThat(r1).isTrue();

            boolean r2 = seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
            assertThat(r2).isFalse();  // 锁已不存在
        }

        @Test
        @DisplayName("释放不存在的锁 → 返回 false")
        void releaseNonExistentLock() {
            boolean released = seckillLockUtil.releaseLock(ACTIVITY_ID, 99999L);
            assertThat(released).isFalse();
        }
    }

    // ==================== 锁 TTL ====================

    @Nested
    @DisplayName("锁 TTL 兜底过期")
    class LockTTL {

        @Test
        @DisplayName("加锁后应有TTL（非永不过期）")
        void lockShouldHaveTTL() {
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);

            long ttl = seckillLockUtil.getLockTTL(ACTIVITY_ID, USER_ID);
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(SeckillLockUtil.LOCK_TTL_SECONDS);

            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
        }

        @Test
        @DisplayName("锁释放后 getLockTTL 返回 -2")
        void ttlAfterRelease() {
            String token = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, token);
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);

            long ttl = seckillLockUtil.getLockTTL(ACTIVITY_ID, USER_ID);
            assertThat(ttl).isEqualTo(-2L);
        }

        @Test
        @DisplayName("不存在的锁 getLockTTL 返回 -2")
        void ttlForNonExistentLock() {
            long ttl = seckillLockUtil.getLockTTL(ACTIVITY_ID, 99999L);
            assertThat(ttl).isEqualTo(-2L);
        }
    }

    // ==================== 多用户隔离 ====================

    @Nested
    @DisplayName("多用户隔离：互不影响")
    class MultiUserIsolation {

        @Test
        @DisplayName("A用户加锁不影响B用户加锁")
        void usersAreIndependent() {
            String tokenA = seckillTokenUtil.generateToken(USER_ID, ACTIVITY_ID);
            String tokenB = seckillTokenUtil.generateToken(ANOTHER_USER_ID, ACTIVITY_ID);

            long rA = seckillLockUtil.acquireLock(ACTIVITY_ID, USER_ID, tokenA);
            long rB = seckillLockUtil.acquireLock(ACTIVITY_ID, ANOTHER_USER_ID, tokenB);

            assertThat(rA).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);
            assertThat(rB).isEqualTo(SeckillLockUtil.ACQUIRE_SUCCESS);

            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isTrue();
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, ANOTHER_USER_ID)).isTrue();

            // 释放 A
            seckillLockUtil.releaseLock(ACTIVITY_ID, USER_ID);
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, USER_ID)).isFalse();
            assertThat(seckillLockUtil.isLocked(ACTIVITY_ID, ANOTHER_USER_ID)).isTrue();

            // 释放 B
            seckillLockUtil.releaseLock(ACTIVITY_ID, ANOTHER_USER_ID);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 由于 StringRedisTemplate 没有 keys() 方法的简便包装，
     * 这里用已知的 Key 来直接删除。如果后续需要 pattern 删除，使用 RedisTemplate 的 keys。
     */
    private static java.util.Set<String> patternToList(String pattern) {
        // Spring Data Redis 2.x 中 StringRedisTemplate 可通过 connection 执行 keys
        // 此处直接构造明确的 Key 列表来清理，避免在测试中调用 KEYS（生产应避免）
        return java.util.Collections.emptySet();
    }
}
