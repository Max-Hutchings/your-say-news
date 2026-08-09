package com.yoursay.observability;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts malformed JSON values, including unknown enum constants, to the standard safe API
 * error shape instead of exposing Jackson details or returning an empty framework response.
 */
@Provider
@Priority(Priorities.USER)
public class JsonInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Inject
    ApiExceptionMapper apiExceptionMapper;

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return apiExceptionMapper.toResponse(new ApiException(
                "validation",
                "INVALID_REQUEST_VALUE",
                Response.Status.BAD_REQUEST,
                exception.getOriginalMessage(),
                "Invalid request."
        ));
    }
}
