package darkchoco.apmapi.common.exception;

/** 이미 존재하는 값과 충돌할 때 사용한다 (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
