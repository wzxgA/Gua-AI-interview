package com.aims.infra.persistence.service.impl;

import com.aims.infra.persistence.entity.SysUserEntity;
import com.aims.infra.persistence.mapper.SysUserMapper;
import com.aims.infra.persistence.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    public SysUserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public SysUserEntity findByUsername(String username) {
        return sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getUsername, username));
    }

    @Override
    public SysUserEntity findById(Long id) {
        return sysUserMapper.selectById(id);
    }
}
