package com.aims.infra.persistence.service.impl;

import com.aims.infra.persistence.entity.CandidateEntity;
import com.aims.infra.persistence.mapper.CandidateMapper;
import com.aims.infra.persistence.service.CandidateService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link CandidateService} 实现：按姓名归集候选人（与存量回填去重口径一致）。 */
@Service
public class CandidateServiceImpl implements CandidateService {

    private final CandidateMapper candidateMapper;

    public CandidateServiceImpl(CandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public CandidateEntity findOrCreate(String candidateName, String phone, String email) {
        if (candidateName == null || candidateName.isBlank()) {
            return null;
        }
        String name = candidateName.trim();
        List<CandidateEntity> existing =
                candidateMapper.selectList(
                        Wrappers.<CandidateEntity>lambdaQuery()
                                .eq(CandidateEntity::getCandidateName, name)
                                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        CandidateEntity entity = new CandidateEntity();
        entity.setCandidateName(name);
        entity.setPhone(phone);
        entity.setEmail(email);
        candidateMapper.insert(entity);
        return entity;
    }
}
