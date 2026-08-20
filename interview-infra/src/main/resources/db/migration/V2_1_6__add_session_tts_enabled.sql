-- v1.4-D 面试官 TTS 语音可选化：按场次记录是否启用语音播报
ALTER TABLE interview_session
    ADD COLUMN tts_enabled BOOLEAN NOT NULL DEFAULT FALSE;