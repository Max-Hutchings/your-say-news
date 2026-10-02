import type { Href } from "expo-router";
import type { UnwrappedFeatures } from "../types";

/**
 * Where a voter lands after voting or tapping "See how others voted". Only with Unwrapped enabled
 * and the Unwrap button flag off does the vote open Unwrapped directly; every other case,
 * including flags that have not loaded yet, opens the results page.
 */
export function postVoteHref(postId: number, features: UnwrappedFeatures | null): Href {
  const opensUnwrappedDirectly = features?.enabled === true && !features.unwrapButton;
  return (opensUnwrappedDirectly
    ? `/posts/${postId}/unwrapped`
    : `/posts/${postId}/results`) as Href;
}
