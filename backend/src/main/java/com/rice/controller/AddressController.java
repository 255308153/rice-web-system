package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.Address;
import com.rice.service.AddressService;
import com.rice.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping
    public Result<List<Address>> getAddressList(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return Result.success(addressService.getAddressList(userId));
    }

    @PostMapping
    public Result<Void> addAddress(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody Address address) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        address.setUserId(userId);
        addressService.addAddress(address);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@RequestHeader("Authorization") String authHeader,
                                    @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        addressService.setDefault(id, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return Result.success();
    }
}
