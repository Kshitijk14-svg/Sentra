package in.sentra.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class SsrfExceptionHandler {

    @ExceptionHandler(SsrfException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onSsrf(SsrfException e) {
        return Map.of("error", e.getMessage());
    }
}
