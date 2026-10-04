package com.yoursay.platform.mobiletelemetry.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiOutcomeTest {

    @ParameterizedTest(name = "status {0} / {1} -> {2} {3} {4}")
    @CsvSource({
            "200, '',        success, none,      none",
            "304, '',        success, none,      none",
            "400, '',        error,   http_400,  none",
            "409, '',        error,   http_409,  none",
            "499, '',        error,   http_499,  none",
            "500, '',        fault,   none,      http_500",
            "503, '',        fault,   none,      http_503",
            "0,   network,   fault,   none,      network_error",
            "0,   timeout,   fault,   none,      timeout",
            "0,   cancelled, error,   cancelled, none",
            "0,   gibberish, fault,   none,      unknown"
    })
    void classifiesByContractNotByException(int status, String errorKind, String outcome,
                                            String errorCode, String faultCode) {
        assertEquals(new ApiOutcome(outcome, errorCode, faultCode), ApiOutcome.classify(status, errorKind));
    }
}
