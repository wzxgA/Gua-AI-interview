package com.aims.infra.service;

import com.aims.core.interview.InterviewerPersona;

/** TTS 语音合成服务接口（与提供商无关）。 */
public interface TtsService {

    /**
     * 将文本转为语音，上传 MinIO，返回音频 URL 与时长。
     *
     * @param text 待合成文本
     * @param persona 面试官人设（用于音色联动）
     * @return 合成结果；失败返回 null（静默降级，不抛异常）
     */
    TtsResult synthesize(String text, InterviewerPersona persona);

    /** TTS 合成结果。 */
    record TtsResult(String audioUrl, int durationMs) {}
}
