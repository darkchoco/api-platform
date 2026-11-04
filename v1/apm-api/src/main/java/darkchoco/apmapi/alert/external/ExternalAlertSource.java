package darkchoco.apmapi.alert.external;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import java.util.List;

/** apm_alert 라벨이 붙은 항목을 알림으로 가져오는 외부 소스. */
public interface ExternalAlertSource {

    AlertSourceType sourceType();

    /** 접속 정보가 모두 설정되어 있는가. 아니면 소스를 비활성으로 보고 건너뛴다. */
    boolean isConfigured();

    /**
     * 지금 열려 있는(미완료) 항목 전부. 일부만 가져오면 나머지 알림이 잘못 닫히므로,
     * 중간에 실패하면 결과를 돌려주지 않고 예외를 던진다.
     */
    List<ExternalAlert> fetchOpenAlerts();
}
