package darkchoco.apmapi.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // 컨테이너/호스트 시간대와 무관하게 월 판정(v{YYMM})과 ins_date/upd_date가 항상 Asia/Seoul 기준이 되도록 고정한다.
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
