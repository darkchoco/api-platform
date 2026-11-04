package darkchoco.apmapi.alert.external;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** 외부 알림 소스 호출용 HTTP 설정. 응답이 없는 외부 서비스 때문에 기동·새로고침이 멈추지 않도록 타임아웃을 건다. */
final class ExternalHttpClients {

    private ExternalHttpClients() {
    }

    /** 연결과 응답 읽기에 모두 같은 타임아웃을 건다. */
    static ClientHttpRequestFactory requestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
