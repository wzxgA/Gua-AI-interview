package com.aims.gateway.controller.interview;

/** 防作弊事件响应项。 */
public record ProctorEventResponse(
        Long id, String eventType, String occurredAt, Long durationMs, String detail) {}
