package com.smartseckill.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultCodePhase0Test {

    @Test
    void couponErrorCodes_shouldExistIn1600Range() {
        assertEquals(1601, ResultCode.COUPON_NOT_FOUND.getCode());
        assertEquals(1602, ResultCode.COUPON_EXPIRED.getCode());
        assertEquals(1603, ResultCode.COUPON_ALREADY_CLAIMED.getCode());
        assertEquals(1604, ResultCode.COUPON_SOLD_OUT.getCode());
        assertEquals(1605, ResultCode.COUPON_MIN_AMOUNT_NOT_MET.getCode());
        assertEquals(1606, ResultCode.COUPON_ALREADY_USED.getCode());
        assertEquals(1607, ResultCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    void couponErrorCodes_shouldHaveMessages() {
        assertTrue(ResultCode.COUPON_NOT_FOUND.getMessage().contains("优惠券"));
        assertTrue(ResultCode.COUPON_EXPIRED.getMessage().contains("过期"));
    }
}
