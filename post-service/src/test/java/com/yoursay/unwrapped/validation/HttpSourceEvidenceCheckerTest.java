package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpSourceEvidenceCheckerTest {
    private static final String PUBLIC_URL = "https://www.ons.gov.uk/data";

    @Test
    void acceptsAReachablePublicSource() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = response(204, Map.of());
        when(client.<Void>send(any(HttpRequest.class), any())).thenReturn(response);
        HttpSourceEvidenceChecker checker =
                new HttpSourceEvidenceChecker(new SourceUrlPolicy(), client);

        assertDoesNotThrow(() -> checker.verify(source(PUBLIC_URL)));
    }

    @Test
    void validatesEveryRedirectTargetAndRejectsPrivateDestinations() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = response(302,
                Map.of("location", List.of("https://[fc00::1]/internal")));
        when(client.<Void>send(any(HttpRequest.class), any())).thenReturn(response);
        HttpSourceEvidenceChecker checker =
                new HttpSourceEvidenceChecker(new SourceUrlPolicy(), client);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> checker.verify(source(PUBLIC_URL)));

        assertEquals("UNWRAPPED_SOURCE_URL_PRIVATE", error.getMessage());
    }

    @Test
    void rejectsUnreachableSources() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = response(503, Map.of());
        when(client.<Void>send(any(HttpRequest.class), any())).thenReturn(response);
        HttpSourceEvidenceChecker checker =
                new HttpSourceEvidenceChecker(new SourceUrlPolicy(), client);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> checker.verify(source(PUBLIC_URL)));

        assertEquals("UNWRAPPED_SOURCE_UNREACHABLE", error.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Void> response(int status, Map<String, List<String>> headers) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }

    private static UnwrappedSourceDraftV1 source(String url) {
        return new UnwrappedSourceDraftV1("source-1", url,
                "Office for National Statistics", "Public data", SourceClassification.OFFICIAL);
    }
}
