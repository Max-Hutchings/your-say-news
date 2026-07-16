package com.yoursay.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER)
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Inject
    ApiExceptionMapper apiExceptionMapper;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return apiExceptionMapper.toResponse(exception);
    }
}
