import { useEffect, useState } from "react";
import { listTopicTags } from "../services/TopicService";
import type { TopicTag } from "../types";

let cachedTopicTags: TopicTag[] | null = null;
let pendingTopicTags: Promise<TopicTag[]> | null = null;

function loadOnce(): Promise<TopicTag[]> {
  if (cachedTopicTags) return Promise.resolve(cachedTopicTags);
  if (!pendingTopicTags) {
    pendingTopicTags = listTopicTags()
      .then((topicTags) => {
        cachedTopicTags = topicTags;
        return topicTags;
      })
      .finally(() => {
        pendingTopicTags = null;
      });
  }
  return pendingTopicTags;
}

/** Shared catalogue cache used by the feed and composer. */
export function useTopicTags() {
  const [topicTags, setTopicTags] = useState<TopicTag[]>(cachedTopicTags ?? []);
  const [loading, setLoading] = useState(cachedTopicTags === null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void loadOnce()
      .then((loaded) => {
        if (active) setTopicTags(loaded);
      })
      .catch(() => {
        if (active) setError("Topic tags could not be loaded.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return { topicTags, loading, error };
}

/** Test-only cache reset so suites do not leak catalogue state. */
export function resetTopicTagsCacheForTests() {
  cachedTopicTags = null;
  pendingTopicTags = null;
}
