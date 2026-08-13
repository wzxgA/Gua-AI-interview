package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.InterviewRoundEntity;
import java.util.List;

/** 面试轮次持久化服务。 */
public interface InterviewRoundService {

    /** 创建新轮次（question 已知，answer 为空）。返回实体含 ID。 */
    InterviewRoundEntity createRound(Long sessionId, int seq, String question);

    /**
     * 创建追问轮次（seq 为 null，followUpType 非空标识追问，parentSeq 指向主问题 seq，followUpIndex 为同主问题下第几次追问）。返回实体含
     * ID。
     */
    InterviewRoundEntity createRound(
            Long sessionId,
            Integer seq,
            String question,
            String followUpType,
            int parentSeq,
            int followUpIndex);

    /** 更新轮次回答。 */
    InterviewRoundEntity updateAnswer(Long roundId, String answer);

    /** 更新轮次 TTS 音频信息。 */
    void updateAudio(Long roundId, String audioUrl, int durationMs);

    /** 查询会话所有轮次，按业务顺序排序：主问题按 seq 升序，追问紧跟所属主问题后按 followUpIndex 升序（不依赖 createdAt，避免追问与后续主问题交错）。 */
    List<InterviewRoundEntity> listBySession(Long sessionId);

    /** 查询会话已回答的轮次数（不含追问）。 */
    int countAnswered(Long sessionId);

    /** 查询某个主问题下的追问次数。 */
    int countFollowUps(Long sessionId, int parentSeq);

    /** 查询会话当前最大序号。 */
    int maxSeq(Long sessionId);
}
