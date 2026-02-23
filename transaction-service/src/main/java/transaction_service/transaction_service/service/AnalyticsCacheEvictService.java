package transaction_service.transaction_service.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnalyticsCacheEvictService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void evictUserAnalytics(Long userId) {
        deleteKeysByPrefix("total:" + userId + ":");
        deleteKeysByPrefix("top:" + userId + ":");
        deleteKeysByPrefix("timeline:" + userId + ":");
    }

    private void deleteKeysByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}