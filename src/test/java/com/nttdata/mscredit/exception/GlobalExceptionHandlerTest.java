package com.nttdata.mscredit.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import com.nttdata.mscredit.generated.dto.ApiError;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

class GlobalExceptionHandlerTest {

    private static final String PATH = "/api/v1/credit-products/64b000000000000000000001";

    private GlobalExceptionHandler handler;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH).build());
    }

    private static void assertBody(ResponseEntity<ApiError> response, int status, String error, String message,
            String path) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status);
        assertThat(response.getBody().getError()).isEqualTo(error);
        assertThat(response.getBody().getMessage()).contains(message);
        assertThat(response.getBody().getPath()).isEqualTo(path);
    }

    @Test
    void businessExceptionUsesItsOwnStatus() {
        ResponseEntity<ApiError> response = handler.handleBusiness(
                new BusinessException(HttpStatus.CONFLICT, "already exists"), exchange);

        assertBody(response, 409, "CONFLICT", "already exists", PATH);
    }

    @Test
    void bindErrorsAreJoinedAsBadRequest() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "must be positive"));
        bindingResult.addError(new FieldError("request", "customerId", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
        WebExchangeBindException exception = new WebExchangeBindException(parameter, bindingResult);

        ResponseEntity<ApiError> response = handler.handleBind(exception, exchange);

        assertBody(response, 400, "BAD_REQUEST", "amount must be positive", PATH);
        assertThat(response.getBody().getMessage()).contains("customerId must not be blank");
    }

    @Test
    void methodValidationErrorsAreJoinedAsBadRequest() {
        MessageSourceResolvable resolvable = mock(MessageSourceResolvable.class);
        when(resolvable.getDefaultMessage()).thenReturn("must be a valid credit id");
        ParameterValidationResult result = mock(ParameterValidationResult.class);
        when(result.getResolvableErrors()).thenReturn(List.of(resolvable));
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
        when(exception.getParameterValidationResults()).thenReturn(List.of(result));

        ResponseEntity<ApiError> response = handler.handleMethodValidation(exception, exchange);

        assertBody(response, 400, "BAD_REQUEST", "must be a valid credit id", PATH);
    }

    @Test
    void constraintViolationsAreJoinedAsBadRequest() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("must not be null");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(exception, exchange);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("must not be null");
    }

    @Test
    void inputErrorsMapToBadRequest() {
        ResponseEntity<ApiError> response = handler.handleInputError(
                new ServerWebInputException("malformed JSON"), exchange);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("malformed JSON");
    }

    @Test
    void unsupportedMediaTypeMapsTo415() {
        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(
                new UnsupportedMediaTypeStatusException("text/plain"), exchange);

        assertBody(response, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", PATH);
    }

    @Test
    void responseStatusExceptionKeepsReason() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "card not found"), exchange);

        assertBody(response, 404, "NOT_FOUND", "card not found", PATH);
    }

    @Test
    void responseStatusExceptionWithoutReasonUsesDefaultMessage() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT), exchange);

        assertBody(response, 409, "CONFLICT", "Request failed", PATH);
    }

    @Test
    void unknownStatusCodeUsesGenericErrorName() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatusCode.valueOf(499), "custom"), exchange);

        assertThat(response.getStatusCode().value()).isEqualTo(499);
        assertThat(response.getBody().getError()).isEqualTo("ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("custom");
    }

    @Test
    void unexpectedExceptionHidesDetailsWithInternalError() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("secret internals"), exchange);

        assertBody(response, 500, "INTERNAL_SERVER_ERROR", "Unexpected error", PATH);
        assertThat(response.getBody().getMessage()).doesNotContain("secret internals");
    }

    @SuppressWarnings("unused")
    private static void sample(String value) {
    }
}
