import { useCallback, useEffect, useState } from "react";
import {
  createAdminTopic,
  getAdminTopics,
  setAdminTopicActive,
  TopicAdminApiError,
} from "../services/topicAdminApi";
import type { AdminTopic, CreateTopicInput } from "../types";

type TopicsError = { status: number | null; message: string };

export function useAdminTopics() {
  const [topics, setTopics] = useState<AdminTopic[] | null>(null);
  const [error, setError] = useState<TopicsError | null>(null);
  const [savingIds, setSavingIds] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setTopics(await getAdminTopics());
    } catch (reason) {
      setError(toTopicsError(reason));
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const add = useCallback(async (input: CreateTopicInput) => {
    setAdding(true);
    setError(null);
    try {
      const created = await createAdminTopic(input);
      setTopics((current) => [...(current ?? []), created].sort(byOrder));
      return created;
    } catch (reason) {
      setError(toTopicsError(reason));
      throw reason;
    } finally {
      setAdding(false);
    }
  }, []);

  const setActive = useCallback(async (topicId: string, active: boolean) => {
    setSavingIds((current) => new Set(current).add(topicId));
    setError(null);
    try {
      const saved = await setAdminTopicActive(topicId, active);
      setTopics((current) => current?.map((topic) => topic.id === saved.id ? saved : topic) ?? null);
      return saved;
    } catch (reason) {
      setError(toTopicsError(reason));
      throw reason;
    } finally {
      setSavingIds((current) => {
        const next = new Set(current);
        next.delete(topicId);
        return next;
      });
    }
  }, []);

  return { topics, error, savingIds, adding, load, add, setActive };
}

const byOrder = (left: AdminTopic, right: AdminTopic) => left.displayOrder - right.displayOrder || left.id.localeCompare(right.id);

function toTopicsError(reason: unknown): TopicsError {
  if (reason instanceof TopicAdminApiError) return { status: reason.status, message: reason.message };
  return { status: null, message: reason instanceof Error ? reason.message : "The topic request failed." };
}
