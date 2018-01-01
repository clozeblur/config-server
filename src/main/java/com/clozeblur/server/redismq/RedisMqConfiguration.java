package com.clozeblur.server.redismq;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Created by clozeblur
 * on 2017/12/20
 */
@Component
public class RedisMqConfiguration {

    @Value("${core.cache.redis.redisNodes}")
    private String redisNodes;
    /**
     * 连接池最大连接数
     */
    @Value("${core.cache.redis.maxTotal:50}")
    private int redis_maxTotal;
    /**
     * 最小连接数
     */
    @Value("${core.cache.redis.minIdle:5}")
    private int redis_minIdle;
    /**
     * 最大空闲连接数
     */
    @Value("${core.cache.redis.maxIdle:30}")
    private int redis_maxIdle;
    /**
     * 获取连接最大等待时间
     */
    @Value("${core.cache.redis.maxWaitMillis:10000}")
    private int redis_maxWaitMillis;

    /**
     * 空闲时是否检查连接可用性
     */
    @Value("${core.cache.redis.testWhileIdle:false}")
    private boolean redis_testWhileIdle;

    /**
     * redis超时时间
     */
    @Value("${core.cache.redis.timeout:5000}")
    private int redis_timeout;

    /**
     * 最大重试次数
     */
    @Value("${core.cache.redis.maxRedirections:6}")
    private int redis_maxRedirections;

    @Bean
    public RedisConnectionFactory connectionFactory() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(this.redis_maxTotal);
        jedisPoolConfig.setMaxIdle(this.redis_maxIdle);
        jedisPoolConfig.setMinIdle(this.redis_minIdle);
        jedisPoolConfig.setMaxWaitMillis(this.redis_maxWaitMillis);
        jedisPoolConfig.setTestWhileIdle(this.redis_testWhileIdle);

        RedisClusterConfiguration configuration = new RedisClusterConfiguration();
        String[] arr = redisNodes.split(",");
        for(String hostAndport : arr){
            String[] harr  = hostAndport.split(":");
            configuration.addClusterNode(new RedisNode(harr[0],Integer.valueOf(harr[1])));
        }
        configuration.setMaxRedirects(this.redis_maxRedirections);

        JedisConnectionFactory connectionFactory = new JedisConnectionFactory(configuration,jedisPoolConfig);
        connectionFactory.setTimeout(this.redis_timeout);
        return connectionFactory;
    }

    @Bean(name = "stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.setValueSerializer(new FastJsonRedisSerializer<>(MessageBody.class));
        return stringRedisTemplate;
    }
}
