package com.abubakr.ecommerceplatform.config;

import com.abubakr.ecommerceplatform.dto.category.CategoryResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, JsonMapper jsonMapper,
                                          @Value("${app.cache.ttl}") Duration ttl) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("ecommerce::");

        // One typed serializer per cache: no class names stored in Redis, and records deserialize correctly.
        JavaType categoryList = jsonMapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(CacheNames.PRODUCTS, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, ProductResponse.class))))
                .withCacheConfiguration(CacheNames.CATEGORIES, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, categoryList))))
                .disableCreateOnMissingCache()
                .transactionAware()
                .build();
    }

    // If Redis is unavailable, log it and read from the database instead of failing the request.
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
