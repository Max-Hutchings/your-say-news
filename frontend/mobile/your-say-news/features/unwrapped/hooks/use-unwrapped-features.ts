import { useEffect, useState } from "react";
import { getUnwrappedFeatures } from "../services/UnwrappedService";
import type { UnwrappedFeatures } from "../types";

/** Used when the flags cannot be loaded: hiding Unwrapped is safer than showing a broken entry. */
const UNAVAILABLE: UnwrappedFeatures = { enabled: false, unwrapButton: false };

let request: Promise<UnwrappedFeatures> | null = null;

/** One request is shared by every feed card; a failure is not cached so the next mount retries. */
function loadFeatures(): Promise<UnwrappedFeatures> {
  if (!request) {
    request = getUnwrappedFeatures().catch(() => {
      request = null;
      return UNAVAILABLE;
    });
  }
  return request;
}

/** Test seam: forgets the shared request so each test starts from an unloaded state. */
export function resetUnwrappedFeatures() {
  request = null;
}

/** The Post Unwrapped flags, or null until the backend has answered. */
export function useUnwrappedFeatures(): UnwrappedFeatures | null {
  const [features, setFeatures] = useState<UnwrappedFeatures | null>(null);

  useEffect(() => {
    let active = true;
    void loadFeatures().then((loaded) => {
      if (active) setFeatures(loaded);
    });
    return () => {
      active = false;
    };
  }, []);

  return features;
}
