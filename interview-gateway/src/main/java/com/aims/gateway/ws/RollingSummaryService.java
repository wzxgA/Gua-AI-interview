package com.aims.gateway.ws;

import com.aims.agent.SummaryAgent;
import com.aims.core.interview.QaPair;
import com.aims.core.interview.SummaryContext;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 滚动摘要管理服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 Redis 读取当前累计摘要和已摘要轮次数
 *   <li>每 5 轮回答后异步触发摘要生成（含追问轮次）
 *   <li>将新摘要和已摘要轮次数写回 Redis（TTL 24h）
 * </ul>
 *
 * <p>使用独立 Redis key 存储 lastSummarizedCount，避免 async 时序导致的轮次计算偏差。
 */
@Service
public class RollingSummaryService {

    private static final Logger log = LoggerFactory.getLogger(RollingSummaryService.class);

    /** 摘要触发间隔：每 5 轮回答触发一次 */
    static final int SUMMARY_INTERVAL = 5;

    /** Redis key 前缀：摘要文本 */
    static final String SUMMARY_KEY_PREFIX = "interview:summary:";

    /** Redis key 前缀：已摘要轮次数 */
    static final String SUMMARY_SEQ_KEY_PREFIX = "interview:summary:seq:";

    /** Redis TTL（小时） */
    static final long SUMMARY_TTL_HOURS = 24;

    private final SummaryAgent summaryAgent;
    private final InterviewRoundService roundService;
    private final InterviewSessionService sessionService;
    private final PositionService positionService;
    private final StringRedisTemplate redisTemplate;

    public RollingSummaryService(
            SummaryAgent summaryAgent,
            InterviewRoundService roundService,
            InterviewSessionService sessionService,
            PositionService positionService,
            StringRedisTemplate redisTemplate) {
        this.summaryAgent = summaryAgent;
        this.roundService = roundService;
        this.sessionService = sessionService;
        this.positionService = positionService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从 Redis 读取当前累计摘要。
     *
     * @param sessionId 面试会话 ID
     * @return 摘要文本，无摘要时返回 null
     */
    public String getRunningSummary(Long sessionId) {
        try {
            return redisTemplate.opsForValue().get(SUMMARY_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("读取滚动摘要失败 sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 从 Redis 读取已摘要的轮次数。
     *
     * @param sessionId 面试会话 ID
     * @return 已摘要轮次数（无摘要时为 0）
     */
    public int getLastSummarizedCount(Long sessionId) {
        try {
            String seqStr = redisTemplate.opsForValue().get(SUMMARY_SEQ_KEY_PREFIX + sessionId);
            if (seqStr != null) {
                return Integer.parseInt(seqStr);
            }
        } catch (Exception e) {
            log.warn("读取已摘要轮次数失败 sessionId={}", sessionId, e);
        }
        return 0;
    }

    /**
     * 异步触发摘要生成（每 5 轮回答后触发一次，含追问轮次）。
     *
     * <p>使用 {@code @Async} 注解，由 Spring 线程池异步执行，不阻塞面试主流程。 通过 lastSummarizedCount 幂等校验避免重复生成。
     *
     * @param sessionId 面试会话 ID
     */
    @Async
    public void triggerSummaryIfNeeded(Long sessionId) {
        try {
            List<InterviewRoundEntity> allRounds = roundService.listBySession(sessionId);
            List<InterviewRoundEntity> answered =
                    allRounds.stream()
                            .filter(r -> r.getAnswer() != null && !r.getAnswer().isBlank())
                            .toList();
            int answeredCount = answered.size();

            if (answeredCount == 0 || answeredCount % SUMMARY_INTERVAL != 0) {
                return;
            }

            // 幂等校验：已摘要轮次 >= 当前回答数则跳过
            int lastSummarizedCount = getLastSummarizedCount(sessionId);
            if (lastSummarizedCount >= answeredCount) {
                return;
            }

            int startIdx = answeredCount - SUMMARY_INTERVAL;
            List<QaPair> roundsToSummarize = new ArrayList<>(SUMMARY_INTERVAL);
            for (int i = startIdx; i < answeredCount; i++) {
                InterviewRoundEntity r = answered.get(i);
                // 追问轮次 seq 为 null，使用列表位置序号
                int seq = r.getSeq() != null ? r.getSeq() : (i + 1);
                roundsToSummarize.add(new QaPair(seq, r.getQuestion(), r.getAnswer()));
            }

            String previousSummary = getRunningSummary(sessionId);

            // 获取岗位名称
            InterviewSessionEntity entity = sessionService.getById(sessionId);
            PositionEntity position = positionService.getById(entity.getPositionId());
            String positionTitle = position.getTitle();

            SummaryContext ctx =
                    new SummaryContext(
                            sessionId, positionTitle, previousSummary, roundsToSummarize, startIdx);
            String newSummary = summaryAgent.summarize(ctx);

            if (newSummary != null && !newSummary.isBlank()) {
                redisTemplate
                        .opsForValue()
                        .set(
                                SUMMARY_KEY_PREFIX + sessionId,
                                newSummary,
                                SUMMARY_TTL_HOURS,
                                TimeUnit.HOURS);
                redisTemplate
                        .opsForValue()
                        .set(
                                SUMMARY_SEQ_KEY_PREFIX + sessionId,
                                String.valueOf(answeredCount),
                                SUMMARY_TTL_HOURS,
                                TimeUnit.HOURS);
                log.info("滚动摘要已更新 sessionId={} summarizedCount={}", sessionId, answeredCount);
            }
        } catch (Exception e) {
            log.warn("触发摘要生成失败 sessionId={}", sessionId, e);
        }
    }
}
