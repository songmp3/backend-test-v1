package im.bigs.pg.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * TestPgClient에서 HTTP 통신을 위해 사용되는 RestTemplate Bean을 정의합니다.
 */
@Configuration
class RestTemplateConfig {
    
    @Bean
    fun restTemplate(): RestTemplate {
        // 실제 운영 환경에서는 Timeout 설정 등이 필요하지만, 과제에서는 기본 생성자로 충분합니다.
        return RestTemplate()
    }
}