package darkchoco.apmapi.common.exception;

/** 요청한 리소스가 없을 때 사용한다 (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
