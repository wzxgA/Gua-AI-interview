package com.aims.core.interview;

/**
 * 单组问答对，用于摘要生成的输入。
 *
 * @param seq 轮次序号（从 1 开始，追问轮次使用其在已回答列表中的位置序号）
 * @param question 面试问题
 * @param answer 候选人回答
 */
public record QaPair(int seq, String question, String answer) {}
