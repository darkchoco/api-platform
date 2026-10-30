package darkchoco.apmapi.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // 컨테이너/호스트 시간대와 무관하게 월 판정(v{YYMM})과 ins_date/upd_date가 항상 app.timezone 기준이 되도록 고정한다.
    // JDBC URL의 serverTimezone(application-dev.yml, application-prd.yml)도 같은 값을 쓴다.
    @Bean
    public Clock clock(@Value("${app.timezone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
