package darkchoco.apmapi.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleUnexpected_hidesInternalDetailsAndReportsRequestPath() {
        var request = new ServletWebRequest(new MockHttpServletRequest("GET", "/apm/accounts"));

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("db down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다");
        assertThat(response.getBody().path()).isEqualTo("/apm/accounts");
    }

    @Test
    void handleValidation_joinsAllFieldErrorsIntoOneMessage() throws NoSuchMethodException {
        var request = new ServletWebRequest(new MockHttpServletRequest("POST", "/apm/accounts"));
        var bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "must not be blank"));
        bindingResult.addError(new FieldError("target", "email", "must be a valid email"));

        Method method = String.class.getMethod("toString");
        var ex = new MethodArgumentNotValidException(new MethodParameter(method, -1), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message())
                .isEqualTo("name: must not be blank, email: must be a valid email");
    }
}
