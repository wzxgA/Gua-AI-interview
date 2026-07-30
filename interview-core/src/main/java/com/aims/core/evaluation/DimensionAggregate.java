package com.aims.core.evaluation;

import java.util.EnumMap;
import java.util.Map;

/**
 * 维度聚合结果：各维度的平均分与评分次数。
 *
 * <p>key 为 {@link EvaluationDimension}，value 为 {@link DimensionScore}。
 */
public class DimensionAggregate {

    private final Map<EvaluationDimension, DimensionScore> scores =
            new EnumMap<>(EvaluationDimension.class);

    /** 累加一次评分。 */
    public void add(EvaluationDimension dim, int score) {
        DimensionScore existing = scores.get(dim);
        if (existing == null) {
            scores.put(dim, new DimensionScore(score, 1));
        } else {
            scores.put(
                    dim, new DimensionScore(existing.totalScore() + score, existing.count() + 1));
        }
    }

    /** 获取指定维度的聚合结果，不存在返回 null。 */
    public DimensionScore get(EvaluationDimension dim) {
        return scores.get(dim);
    }

    /** 获取全部维度聚合结果。 */
    public Map<EvaluationDimension, DimensionScore> getAll() {
        return scores;
    }

    /** 维度平均分（1-5），无评分返回 0。 */
    public double avgScore(EvaluationDimension dim) {
        DimensionScore ds = scores.get(dim);
        return ds == null || ds.count() == 0 ? 0.0 : (double) ds.totalScore() / ds.count();
    }

    /** 维度评分聚合记录。 */
    public record DimensionScore(int totalScore, int count) {
        /** 平均分。 */
        public double avgScore() {
            return count == 0 ? 0.0 : (double) totalScore / count;
        }
    }
}
