package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rice.entity.Address;
import com.rice.mapper.AddressMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    @Autowired
    private AddressMapper addressMapper;

    public List<Address> getAddressList(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId));
    }

    @Transactional
    public void addAddress(Address address) {
        if (address.getIsDefault() == 1) {
            addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                    .eq(Address::getUserId, address.getUserId())
                    .set(Address::getIsDefault, 0));
        }
        addressMapper.insert(address);
    }

    @Transactional
    public void setDefault(Long id, Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .set(Address::getIsDefault, 0));

        Address address = new Address();
        address.setId(id);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    public void deleteAddress(Long id) {
        addressMapper.deleteById(id);
    }
}
