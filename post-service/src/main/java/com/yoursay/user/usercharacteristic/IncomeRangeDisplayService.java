package com.yoursay.user.usercharacteristic;

import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;

/** Resolves immutable income bucket identities for API and agent-facing aggregate displays. */
public interface IncomeRangeDisplayService {
    IncomeRangeDisplayDto resolveDisplay(String bucketId);
}
