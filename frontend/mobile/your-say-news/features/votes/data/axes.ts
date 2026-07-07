/**
 * The characteristic axes we offer as sentiment breakdowns, plus the display helpers for their
 * buckets. Internal to the votes feature.
 *
 * A curated, high-signal subset of the captured characteristics (not every field) — each `field`
 * is the exact backend axis name sent to `GET /votes/{postId}/sentiment/{axis}`, paired with the
 * human label shown on its selector chip. Order is the order the chips appear in.
 */
export interface SentimentAxis {
  /** Backend axis field name (e.g. `politicalPersuasion`). */
  field: string;
  /** Chip label shown to the user. */
  label: string;
}

export const SENTIMENT_AXES: readonly SentimentAxis[] = [
  { field: "politicalPersuasion", label: "Political leaning" },
  { field: "ageRange", label: "Age" },
  { field: "gender", label: "Gender" },
  { field: "race", label: "Race" },
  { field: "country", label: "Country" },
  { field: "region", label: "Region" },
  { field: "urbanRural", label: "Urban / rural" },
  { field: "religion", label: "Religion" },
  { field: "education", label: "Education" },
  { field: "personalIncomeRange", label: "Income" },
];

/**
 * Turn a raw bucket enum name into a readable label:
 * `LEFT` → "Left", `CENTRE_LEFT` → "Centre Left", `AGE_25_34` → "Age 25–34",
 * `AGE_65_PLUS` → "Age 65+". Adjacent number runs join with an en dash and a trailing `PLUS`
 * becomes `+`, so age/income bands read naturally.
 */
export function prettifyBucket(raw: string): string {
  const out: string[] = [];
  for (const token of raw.toLowerCase().split("_")) {
    const last = out.length - 1;
    if (token === "plus" && last >= 0) {
      out[last] = `${out[last]}+`;
    } else if (/^\d+$/.test(token) && last >= 0 && /\d$/.test(out[last])) {
      out[last] = `${out[last]}–${token}`;
    } else {
      out.push(token.charAt(0).toUpperCase() + token.slice(1));
    }
  }
  return out.join(" ");
}
