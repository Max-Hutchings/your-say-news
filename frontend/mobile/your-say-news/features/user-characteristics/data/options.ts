/** Presentation-only choices that are not backend characteristic enums. */

import type { Option } from "../types";

export const YES_NO_OPTIONS: Option[] = [
    { label: "Yes", value: "YES" },
    { label: "No", value: "NO" },
];

export type CurrencyOption = Option & { symbol: string };

// Currency controls label formatting only; the submitted income value is the backend-owned band.
export const CURRENCY_OPTIONS: CurrencyOption[] = [
    { label: "USD", value: "USD", symbol: "USD" },
    { label: "CNY", value: "CNY", symbol: "CNY" },
    { label: "EUR", value: "EUR", symbol: "EUR" },
    { label: "JPY", value: "JPY", symbol: "JPY" },
    { label: "GBP", value: "GBP", symbol: "GBP" },
    { label: "INR", value: "INR", symbol: "INR" },
    { label: "CAD", value: "CAD", symbol: "CAD" },
    { label: "BRL", value: "BRL", symbol: "BRL" },
    { label: "AUD", value: "AUD", symbol: "AUD" },
    { label: "KRW", value: "KRW", symbol: "KRW" },
    { label: "MXN", value: "MXN", symbol: "MXN" },
    { label: "RUB", value: "RUB", symbol: "RUB" },
    { label: "CHF", value: "CHF", symbol: "CHF" },
    { label: "SGD", value: "SGD", symbol: "SGD" },
    { label: "HKD", value: "HKD", symbol: "HKD" },
    { label: "ZAR", value: "ZAR", symbol: "ZAR" },
    { label: "SEK", value: "SEK", symbol: "SEK" },
    { label: "NOK", value: "NOK", symbol: "NOK" },
    { label: "DKK", value: "DKK", symbol: "DKK" },
    { label: "AED", value: "AED", symbol: "AED" },
];
