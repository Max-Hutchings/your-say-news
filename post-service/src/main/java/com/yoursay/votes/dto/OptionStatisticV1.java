package com.yoursay.votes.dto;

public record OptionStatisticV1(
        Long optionId,
        long count,
        double percentage,
        double compositionPercentage,
        double differenceFromOverallPercentagePoints,
        double differenceFromRestPercentagePoints,
        double wilson95Low,
        double wilson95High,
        double rawPValue,
        double adjustedQValue,
        String statisticalTest
) {
    public OptionStatisticV1 withAdjustedQValue(double qValue) {
        return new OptionStatisticV1(optionId, count, percentage, compositionPercentage,
                differenceFromOverallPercentagePoints, differenceFromRestPercentagePoints,
                wilson95Low, wilson95High, rawPValue, qValue, statisticalTest);
    }
}
