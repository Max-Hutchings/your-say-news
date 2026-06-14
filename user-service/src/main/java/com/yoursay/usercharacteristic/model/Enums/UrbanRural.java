package com.yoursay.usercharacteristic.model.Enums;

/**
 * Urban / suburban / rural band — a country-agnostic settlement type so non-UK users are
 * sortable on a region-like axis without relying on the UK-only county field.
 */
public enum UrbanRural {
    URBAN,
    SUBURBAN,
    RURAL,
    PREFER_NOT_TO_SAY
}
