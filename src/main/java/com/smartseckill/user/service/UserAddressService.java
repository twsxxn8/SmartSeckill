package com.smartseckill.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.user.dto.AddressRequest;
import com.smartseckill.user.entity.UserAddress;
import com.smartseckill.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户收货地址业务服务。
 * <p>
 * 每个用户可维护多个地址，但同一时间只有一个默认地址。
 * 下单时通过 addressId 关联收货信息。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressMapper userAddressMapper;

    /** 获取用户地址列表，默认地址排在最前 */
    public List<UserAddress> getAddressList(Long userId) {
        return userAddressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getDeleted, 0)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getCreateTime)
        );
    }

    /** 获取地址详情，校验地址归属当前用户 */
    public UserAddress getAddress(Long userId, Long addressId) {
        UserAddress address = userAddressMapper.selectById(addressId);
        if (address == null || address.getDeleted() == 1 || !userId.equals(address.getUserId())) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    /**
     * 新增地址。
     * 业务规则：设为默认时取消其他默认；首个地址自动设为默认。
     */
    @Transactional
    public void addAddress(Long userId, AddressRequest request) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetailAddress(request.getDetailAddress());
        address.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : 0);

        if (address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        Long count = userAddressMapper.selectCount(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getDeleted, 0)
        );
        if (count == 0) {
            address.setIsDefault(1);
        }

        userAddressMapper.insert(address);
    }

    /** 更新地址，支持切换默认地址 */
    @Transactional
    public void updateAddress(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = getAddress(userId, addressId);

        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetailAddress(request.getDetailAddress());

        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            clearDefaultAddress(userId);
            address.setIsDefault(1);
        }

        userAddressMapper.updateById(address);
    }

    /** 逻辑删除地址（MyBatis-Plus @TableLogic） */
    public void deleteAddress(Long userId, Long addressId) {
        getAddress(userId, addressId);
        userAddressMapper.deleteById(addressId);
    }

    /** 取消该用户所有默认地址标记 */
    private void clearDefaultAddress(Long userId) {
        UserAddress updateEntity = new UserAddress();
        updateEntity.setIsDefault(0);
        userAddressMapper.update(updateEntity,
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getIsDefault, 1)
        );
    }
}
