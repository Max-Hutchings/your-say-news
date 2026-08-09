import { useEffect, useState } from "react";
import { listTopics } from "../services/TopicService";
import type { Topic } from "../types";

let cachedTopics: Topic[] | null = null;
let pendingTopics: Promise<Topic[]> | null = null;

function loadOnce(): Promise<Topic[]> {
  if (cachedTopics) return Promise.resolve(cachedTopics);
  if (!pendingTopics) {
    pendingTopics = listTopics()
      .then((topics) => {
        cachedTopics = topics;
        return topics;
      })
      .finally(() => {
        pendingTopics = null;
      });
  }
  return pendingTopics;
}

/** Shared catalogue cache used by the feed and composer. */
export function useTopics() {
  const [topics, setTopics] = useState<Topic[]>(cachedTopics ?? []);
  const [loading, setLoading] = useState(cachedTopics === null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void loadOnce()
      .then((loaded) => {
        if (active) setTopics(loaded);
      })
      .catch(() => {
        if (active) setError("Topics could not be loaded.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return { topics, loading, error };
}

/** Test-only cache reset so suites do not leak catalogue state. */
export function resetTopicsCacheForTests() {
  cachedTopics = null;
  pendingTopics = null;
}
