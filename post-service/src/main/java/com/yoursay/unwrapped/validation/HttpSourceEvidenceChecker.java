package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class HttpSourceEvidenceChecker implements SourceEvidenceChecker {
    private static final int MAX_REDIRECTS = 5;

    private final SourceUrlPolicy urlPolicy;
    private final HttpClient client;

    @Inject
    public HttpSourceEvidenceChecker(SourceUrlPolicy urlPolicy) {
        this(urlPolicy, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpSourceEvidenceChecker(SourceUrlPolicy urlPolicy, HttpClient client) {
        this.urlPolicy = urlPolicy;
        this.client = client;
    }

    @Override
    public void verify(UnwrappedSourceDraftV1 source) {
        URI current = urlPolicy.validate(source.url());
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpResponse<Void> response = sendHead(current);
            int status = response.statusCode();
            if (status == 403 || status == 405) {
                response = sendGet(current);
                status = response.statusCode();
            }
            if (status >= 200 && status < 300) return;
            if (status < 300 || status >= 400) {
                throw new IllegalArgumentException(
                        "UNWRAPPED_SOURCE_UNREACHABLE: url=" + current + " status=" + status);
            }
            String location = response.headers().firstValue("location").orElse(null);
            if (location == null) {
                throw new IllegalArgumentException(
                        "UNWRAPPED_SOURCE_REDIRECT_INVALID: url=" + current + " status=" + status);
            }
            current = urlPolicy.validate(current.resolve(location).toString());
        }
        throw new IllegalArgumentException("UNWRAPPED_SOURCE_REDIRECT_LIMIT: url=" + current);
    }

    private HttpResponse<Void> sendHead(URI uri) {
        return send(uri, "HEAD");
    }

    private HttpResponse<Void> sendGet(URI uri) {
        return send(uri, "GET");
    }

    private HttpResponse<Void> send(URI uri, String method) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Pepper-Unwrapped-Source-Checker/1.0");
            if ("GET".equals(method)) request.header("Range", "bytes=0-0");
            return client.send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(
                    "UNWRAPPED_SOURCE_UNREACHABLE: url=" + uri + " cause=interrupted", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("UNWRAPPED_SOURCE_UNREACHABLE: url=" + uri
                    + " cause=" + e.getClass().getSimpleName(), e);
        }
    }
}
