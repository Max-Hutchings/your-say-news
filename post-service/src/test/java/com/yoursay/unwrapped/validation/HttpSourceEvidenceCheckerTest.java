package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @ParameterizedTest
    @ValueSource(ints = {403, 405})
    void fallsBackToBoundedGetWhenServerRejectsHead(int headStatus) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> headResponse = response(headStatus, Map.of());
        HttpResponse<Void> getResponse = response(206, Map.of());
        when(client.<Void>send(any(HttpRequest.class), any()))
                .thenReturn(headResponse, getResponse);
        HttpSourceEvidenceChecker checker =
                new HttpSourceEvidenceChecker(new SourceUrlPolicy(), client);

        assertDoesNotThrow(() -> checker.verify(source(PUBLIC_URL)));

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any());
        assertEquals(List.of("HEAD", "GET"), requests.getAllValues().stream()
                .map(HttpRequest::method).toList());
        assertEquals("bytes=0-0", requests.getAllValues().get(1).headers()
                .firstValue("Range").orElseThrow());
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

        assertEquals("UNWRAPPED_SOURCE_UNREACHABLE: url=" + PUBLIC_URL + " status=503",
                error.getMessage());
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
