package com.yoursay.observability;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.time.format.DateTimeParseException;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    @Inject
    DomainMetrics metrics;

    @Override
    public Response toResponse(Throwable exception) {
        ApiException apiException = toApiException(exception);
        SourceLocation source = sourceLocation(exception);
        metrics.recordError(apiException.domain(), "api", apiException.errorCode(), apiException.statusCode());
        withMdc(apiException, source, () -> Log.errorf(exception,
                "API error %s in domain %s at %s:%d: %s",
                apiException.errorCode(), apiException.domain(), source.file(), source.line(), exception.getMessage()));
        return Response.status(apiException.statusCode())
                .entity(new ErrorResponse(apiException.errorCode(), apiException.publicMessage()))
                .build();
    }

    private static ApiException toApiException(Throwable exception) {
        if (exception instanceof ApiException apiException) {
            return apiException;
        }
        if (exception instanceof ConstraintViolationException) {
            return new ApiException("validation", "VALIDATION_FAILED", Response.Status.BAD_REQUEST,
                    exception.getMessage(), "Invalid request.");
        }
        if (exception instanceof IllegalArgumentException || exception instanceof DateTimeParseException) {
            return new ApiException("validation", "INVALID_REQUEST_VALUE", Response.Status.BAD_REQUEST,
                    exception.getMessage(), "Invalid request.");
        }
        if (exception instanceof WebApplicationException webApplicationException) {
            Response.Status status = Response.Status.fromStatusCode(webApplicationException.getResponse().getStatus());
            Response.Status safeStatus = status == null ? Response.Status.INTERNAL_SERVER_ERROR : status;
            return new ApiException("unknown", "REQUEST_FAILED", safeStatus,
                    webApplicationException.getMessage(), ApiException.genericMessage(safeStatus));
        }
        return new ApiException("unknown", "INTERNAL_ERROR", Response.Status.INTERNAL_SERVER_ERROR,
                exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage(),
                "The request could not be processed.");
    }

    private static SourceLocation sourceLocation(Throwable exception) {
        for (StackTraceElement element : exception.getStackTrace()) {
            if (element.getClassName().startsWith("com.yoursay.")
                    && !element.getClassName().startsWith("com.yoursay.observability.")
                    && !element.getClassName().contains(".error.")) {
                String file = element.getFileName() == null ? "unknown" : element.getFileName();
                return new SourceLocation(file, element.getLineNumber());
            }
        }
        return new SourceLocation("unknown", -1);
    }

    private static void withMdc(ApiException exception, SourceLocation source, Runnable log) {
        MDC.put("domain", exception.domain());
        MDC.put("errorCode", exception.errorCode());
        MDC.put("httpStatus", exception.statusCode());
        MDC.put("sourceFile", source.file());
        MDC.put("sourceLine", source.line());
        try {
            log.run();
        } finally {
            MDC.remove("domain");
            MDC.remove("errorCode");
            MDC.remove("httpStatus");
            MDC.remove("sourceFile");
            MDC.remove("sourceLine");
        }
    }

    private record SourceLocation(String file, int line) {
    }
}
