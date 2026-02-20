package kr.jemi.zticket.config;

import io.hypersistence.tsid.TSID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TsidConfig {

    @Bean
    public TSID.Factory tsidFactory(StringRedisTemplate redisTemplate,
                                    @Value("${zticket.tsid.node-bits}") int nodeBits) {
        int maxNodeCount = 1 << nodeBits;
        Long counter = redisTemplate.opsForValue().increment("zticket:tsid:node:counter");
        int nodeId = (int) (counter % maxNodeCount);

        return TSID.Factory.builder()
                .withNodeBits(nodeBits)
                .withNode(nodeId)
                .build();
    }
}
