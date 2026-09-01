package com.test.test.exam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 배치 활성화. 테스트 프로파일에서는 비활성(불필요한 배치 기동 방지).
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
