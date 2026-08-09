/**
 * Owns the complete Post Unwrapped journey: aggregate evidence selection, versioned stories,
 * review, delivery and the isolated reconsideration response.
 *
 * <p>Only controllers and public service interfaces belong at this package level. Cross-boundary
 * DTOs live in {@code dto}; persistence, orchestration, statistical selection and model integration
 * are internal subpackages.</p>
 */
package com.yoursay.unwrapped;
