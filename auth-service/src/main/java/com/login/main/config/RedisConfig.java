package com.login.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 快取配置類別
 * 
 * 負責定義 RedisTemplate 的序列化策略，確保 Key 為字串，而 Value 為 JSON 格式，
 * 以利於跨語言與易讀性的資料儲存。
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     * 
     * 定義 Redis 資料存取模版，自定義 Key 與 Value 的序列化器。
     * @param connectionFactory Redis 連線工廠
     * @return 經配置後的 RedisTemplate 實體
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());

        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}