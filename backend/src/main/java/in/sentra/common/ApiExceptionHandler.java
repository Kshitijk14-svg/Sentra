package in.sentra.common;

import in.sentra.check.InvalidCheckException;
import in.sentra.security.SsrfException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns client mistakes into a 400 with a one-line, user-readable {"error": ...} body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({SsrfException.class, InvalidCheckException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onBadRequest(RuntimeException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return Map.of("error", first == null ? "Invalid request"
                : first.getField() + ": " + first.getDefaultMessage());
    }
}
