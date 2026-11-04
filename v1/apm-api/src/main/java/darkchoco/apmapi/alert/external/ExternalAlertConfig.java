package darkchoco.apmapi.alert.external;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JiraProperties.class, TodoistProperties.class})
public class ExternalAlertConfig {
}
