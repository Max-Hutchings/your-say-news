package com.yoursay.votes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;

/**
 * One characteristic value defining membership of an aggregate cohort.
 *
 * <p>A single-characteristic cohort:</p>
 * <pre>{@code
 * List.of(new CohortDimensionV1("ageRange", "AGE_25_34"))
 * }</pre>
 *
 * <p>A two-characteristic intersection:</p>
 * <pre>{@code
 * List.of(
 *     new CohortDimensionV1("ageRange", "AGE_25_34"),
 *     new CohortDimensionV1("gender", "MAN")
 * )
 * }</pre>
 *
 * @param axis characteristic being grouped, such as {@code ageRange} or {@code gender}
 * @param bucket grouped value, such as {@code AGE_25_34} or {@code MAN}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CohortDimensionV1(
        String axis,
        String bucket,
        String label,
        IncomeRangeDisplayDto income
) {
    public CohortDimensionV1(String axis, String bucket) {
        this(axis, bucket, null, null);
    }
}
