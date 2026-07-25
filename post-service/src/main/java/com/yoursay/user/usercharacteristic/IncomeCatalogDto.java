package com.yoursay.user.usercharacteristic;

import java.util.List;

/** Versioned list of income profiles supported by the current backend deployment. */
public record IncomeCatalogDto(
        String catalogVersion,
        List<IncomeProfileSummaryDto> profiles
) {
}
