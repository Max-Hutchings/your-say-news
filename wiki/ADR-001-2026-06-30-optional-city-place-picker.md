# ADR-001: Optional City Place Picker

Date: 2026-06-30

## Situation

Your Say News collects user characteristics so voting results can be shown as anonymous aggregates.
City may be useful for future geographic breakdowns, but free-text city entry is unreliable because
different users can spell, abbreviate, translate, or mistype the same city in different ways.

City is also more privacy-sensitive than broader fields such as country or region. Some cities or
small towns may create buckets that are too small for safe aggregation.

## Options Considered

1. Make city required as free text.
   This would gather more data, but it would create inconsistent values and increase privacy risk.

2. Make city required from a fixed enum.
   This would normalize values, but maintaining a global enum of cities is impractical and would age
   badly as names, boundaries, and local conventions change.

3. Make city required through a third-party place picker.
   This would normalize values, but it forces users to provide precise location data before they can
   use the app.

4. Keep city optional and later collect it through a searchable place picker.
   This keeps onboarding lighter, reduces privacy pressure, and still allows normalized city data
   when users choose to provide it.

## Decision

City stays optional. In the UI, it should be described as "city / nearest city" so users outside a
major city can provide the closest meaningful urban area without feeling forced to invent an exact
locality.

When city is collected, it should not be stored as raw free text for analytics. It should be captured
through a searchable place picker/autocomplete backed by a managed location authority, and stored
with normalized fields such as city name, admin region, country code, provider, and stable place ID.

## Reason

Optional city collection best fits the product's privacy promise and avoids blocking onboarding on a
high-friction field. A place picker avoids spelling and translation drift without requiring the team
to maintain a world-city enum.

Country and region should remain the primary geographic aggregation fields. City-level aggregation
should only be shown when enough users exist in a bucket to protect anonymity.

## Consequences

- Do not create a hand-maintained enum of world cities.
- Do not rely on raw free-text city values for reporting.
- Future implementation should evaluate providers such as Google Places, Mapbox, HERE, Loqate,
  Melissa, Precisely, Smarty, or an open-data option such as GeoNames.
- Any city-level analytics must enforce minimum bucket-size privacy rules before display.
