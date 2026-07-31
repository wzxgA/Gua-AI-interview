package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.SysUserEntity;

/** 用户查询服务。 */
public interface SysUserService {

    /** 按用户名查询用户（含密码），用于登录校验。 */
    SysUserEntity findByUsername(String username);

    /** 按 ID 查询用户（不含密码）。 */
    SysUserEntity findById(Long id);
}
