package darkchoco.apmapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

// DB에 접속하지 않고 application*.yml만 읽어, app.timezone 하나가 Clock과 JDBC serverTimezone에 함께 쓰이는지 확인한다.
class TimezoneSettingTest {

    private ApplicationContextRunner runnerWithProfile(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> TestPropertyValues.of("spring.profiles.active=" + profile).applyTo(context))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ClockConfig.class)
                .withPropertyValues("DB_PASSWORD=unused");
    }

    @Test
    void defaultsToEuropeBerlinForClockAndJdbcUrlInDev() {
        runnerWithProfile("dev").run(context -> {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .contains("serverTimezone=Europe/Berlin");
        });
    }

    @Test
    void defaultsToEuropeBerlinForClockAndJdbcUrlInPrd() {
        runnerWithProfile("prd").run(context -> {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .contains("serverTimezone=Europe/Berlin");
        });
    }

    @Test
    void overridingOneSettingChangesClockAndJdbcUrlTogether() {
        runnerWithProfile("prd").withPropertyValues("app.timezone=Asia/Tokyo").run(context -> {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .contains("serverTimezone=Asia/Tokyo");
        });
    }

    @Test
    void invalidTimezoneFailsStartup() {
        runnerWithProfile("dev").withPropertyValues("app.timezone=Not/AZone")
                .run(context -> assertThat(context).hasFailed());
    }
}
