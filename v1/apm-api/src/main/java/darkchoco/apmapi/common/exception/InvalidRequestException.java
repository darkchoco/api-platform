package darkchoco.apmapi.common.exception;

/** 요청 값이 업무 규칙을 위반했을 때 사용한다 (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
