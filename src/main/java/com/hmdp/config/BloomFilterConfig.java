package com.hmdp.config;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.nio.charset.Charset;

@Configuration
public class BloomFilterConfig {
    @Value("${bloom-filter.expected-insertions}")
    private int expectedInsertions;

    @Value("${bloom-filter.fpp}")
    private double falsePositiveProbability;

    @Bean
    public BloomFilter<String> bloomFilter() {
        return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedInsertions, falsePositiveProbability);
    }
}
