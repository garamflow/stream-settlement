package com.github.garamflow.streamsettlement.redis.dto;

public record AbusingKey(
        Long memberId,
        Long contentId,
        Long creatorId,
        String ip
) {
    public static AbusingKey of(Long memberId, Long contentId, Long creatorId, String ip) {
        return new AbusingKey(memberId, contentId, creatorId, ip);
    }
}
