package darkchoco.apmapi.common.exception;

/**
 * 요청 형식은 맞지만 지금 처리할 수 없을 때 사용한다 (HTTP 422).
 * 이 API에서 422는 환율 누락(FX_RATE_MISSING) 한 가지 의미로만 쓴다 - 다른 의미가 생기면 ErrorResponse에 code를 도입한다.
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
