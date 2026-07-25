package com.yoursay.votes.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PostAnalysisStatistics {
    private static final double Z_95 = 1.959963984540054;

    private PostAnalysisStatistics() {
    }

    static double[] wilson95(long successes, long total) {
        if (total == 0) return new double[]{0.0, 0.0};
        double p = (double) successes / total;
        double z2 = Z_95 * Z_95;
        double denominator = 1.0 + z2 / total;
        double centre = (p + z2 / (2.0 * total)) / denominator;
        double margin = Z_95 * Math.sqrt((p * (1.0 - p) + z2 / (4.0 * total)) / total) / denominator;
        return new double[]{100.0 * (centre - margin), 100.0 * (centre + margin)};
    }

    static TestResult compare(long cohortSuccesses, long cohortTotal,
                              long restSuccesses, long restTotal) {
        long a = cohortSuccesses;
        long b = cohortTotal - cohortSuccesses;
        long c = restSuccesses;
        long d = restTotal - restSuccesses;
        if (cohortTotal == 0 || restTotal == 0) return new TestResult(1.0, "NONE");
        double expectedMinimum = Math.min(
                (double) (a + c) * (a + b) / (a + b + c + d),
                (double) (b + d) * (a + b) / (a + b + c + d));
        if (expectedMinimum < 5.0) {
            return new TestResult(fisherTwoSided(a, b, c, d), "FISHER_EXACT");
        }
        double p1 = (double) a / cohortTotal;
        double p2 = (double) c / restTotal;
        double pooled = (double) (a + c) / (cohortTotal + restTotal);
        double standardError = Math.sqrt(pooled * (1.0 - pooled)
                * (1.0 / cohortTotal + 1.0 / restTotal));
        if (standardError == 0.0) return new TestResult(1.0, "TWO_PROPORTION_Z");
        double z = Math.abs(p1 - p2) / standardError;
        return new TestResult(Math.min(1.0, 2.0 * (1.0 - normalCdf(z))), "TWO_PROPORTION_Z");
    }

    static double[] benjaminiHochberg(List<Double> pValues) {
        int size = pValues.size();
        double[] adjusted = new double[size];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) order.add(i);
        order.sort(Comparator.comparingDouble(pValues::get).thenComparingInt(Integer::intValue));
        double next = 1.0;
        for (int rank = size; rank >= 1; rank--) {
            int index = order.get(rank - 1);
            next = Math.min(next, pValues.get(index) * size / rank);
            adjusted[index] = Math.min(1.0, next);
        }
        return adjusted;
    }

    private static double normalCdf(double value) {
        return 0.5 * (1.0 + erf(value / Math.sqrt(2.0)));
    }

    // Abramowitz and Stegun 7.1.26; maximum error is about 1.5e-7.
    private static double erf(double value) {
        double sign = value < 0 ? -1.0 : 1.0;
        double x = Math.abs(value);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double polynomial = (((((1.061405429 * t - 1.453152027) * t)
                + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        return sign * (1.0 - polynomial * Math.exp(-x * x));
    }

    private static double fisherTwoSided(long a, long b, long c, long d) {
        long row1 = a + b;
        long row2 = c + d;
        long col1 = a + c;
        long total = row1 + row2;
        long min = Math.max(0, col1 - row2);
        long max = Math.min(row1, col1);
        double observed = hypergeometric(a, row1, col1, total);
        double sum = 0.0;
        for (long value = min; value <= max; value++) {
            double probability = hypergeometric(value, row1, col1, total);
            if (probability <= observed + 1e-12) sum += probability;
        }
        return Math.min(1.0, sum);
    }

    private static double hypergeometric(long value, long draws, long successes, long population) {
        return Math.exp(logCombination(successes, value)
                + logCombination(population - successes, draws - value)
                - logCombination(population, draws));
    }

    private static double logCombination(long n, long k) {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY;
        long use = Math.min(k, n - k);
        double result = 0.0;
        for (long i = 1; i <= use; i++) {
            result += Math.log(n - use + i) - Math.log(i);
        }
        return result;
    }

    record TestResult(double pValue, String method) {
    }
}
