package darkchoco.apmapi.common.exception;

import jakarta.servlet.ServletException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("요청 검증에 실패했습니다");

        return ResponseEntity.badRequest()
                .body(errorResponse(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(errorResponse(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = "%s: 값 형식이 올바르지 않습니다".formatted(ex.getName());

        return ResponseEntity.badRequest()
                .body(errorResponse(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorResponse(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableEntityException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request));
    }

    /**
     * 존재하지 않는 경로(404), 허용되지 않는 메서드(405) 같은 Spring MVC 자체 예외.
     * 이 예외들은 상태 코드를 스스로 갖고 있으므로 그대로 따르고, 5xx면 예상 밖 오류로 취급한다.
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ErrorResponse> handleServletException(ServletException ex, WebRequest request) {
        if (!(ex instanceof org.springframework.web.ErrorResponse frameworkError)
                || frameworkError.getStatusCode().is5xxServerError()) {
            return handleUnexpected(ex, request);
        }

        HttpStatus status = HttpStatus.valueOf(frameworkError.getStatusCode().value());

        return ResponseEntity.status(status)
                .headers(frameworkError.getHeaders())
                .body(errorResponse(status, messageFor(status), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        LOGGER.error("처리되지 않은 예외 발생", ex);

        return ResponseEntity.internalServerError()
                .body(errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다", request));
    }

    private String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "요청한 경로를 찾을 수 없습니다";
            case METHOD_NOT_ALLOWED -> "허용되지 않는 요청 방식입니다";
            case UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 요청 형식입니다";
            default -> "요청을 처리할 수 없습니다";
        };
    }

    private ErrorResponse errorResponse(HttpStatus status, String message, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
