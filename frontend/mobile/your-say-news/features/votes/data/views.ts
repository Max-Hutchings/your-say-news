/**
 * The chart types the results sheet can render a breakdown as. Internal to the votes feature.
 *
 * Every view shows the SAME aggregated numbers — only the shape changes, so the user picks the
 * reading that suits them (raw counts, share-that-agree, a ranked table, or column heights). Order
 * is the order the tabs appear in. `caption` is the small mono note shown on the right of the
 * chart header, describing what that view emphasises.
 */
export type SentimentViewKey = "counts" | "bars" | "table" | "columns";

export interface SentimentView {
  key: SentimentViewKey;
  /** Tab label in the "View as" selector. */
  label: string;
  /** Right-aligned chart-header note describing the view. */
  caption: string;
}

export const SENTIMENT_VIEWS: readonly SentimentView[] = [
  { key: "counts", label: "Counts", caption: "Number of votes" },
  { key: "bars", label: "Bars", caption: "Share that agree" },
  { key: "table", label: "Table", caption: "Sorted by total" },
  { key: "columns", label: "Columns", caption: "Height = total votes" },
];
