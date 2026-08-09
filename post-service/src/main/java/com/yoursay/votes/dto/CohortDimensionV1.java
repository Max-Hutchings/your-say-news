package com.yoursay.votes.dto;

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
public record CohortDimensionV1(String axis, String bucket) {
}
