package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.CandidateEntity;

/** 候选人服务（v1.1-C）：简历与候选人解耦后，上传/创建面试时按姓名归集候选人。 */
public interface CandidateService {

    /**
     * 按姓名查找候选人，不存在则创建。
     *
     * @param candidateName 候选人姓名（空白时返回 null，不建档）
     * @param phone 联系电话（仅新建时写入）
     * @param email 邮箱（仅新建时写入）
     * @return 候选人实体；姓名空白时返回 null
     */
    CandidateEntity findOrCreate(String candidateName, String phone, String email);
}
