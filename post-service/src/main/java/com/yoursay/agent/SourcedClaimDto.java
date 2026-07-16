package com.yoursay.agent;

import java.util.List;

public record SourcedClaimDto(
        String text,
        List<String> sourceUrls
) {
}
