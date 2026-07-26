import { useCallback, useEffect, useState } from "react";
import { AdminApiError, getAdminUsers, updateAdminUser } from "../services/userAdminApi";
import type { AdminUser, AdminUserUpdate } from "../types";

type UsersError = {
  status: number | null;
  message: string;
};

export function useAdminUsers() {
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [error, setError] = useState<UsersError | null>(null);
  const [savingUserIds, setSavingUserIds] = useState<Set<number>>(new Set());

  const load = useCallback(async () => {
    setError(null);
    try {
      setUsers(await getAdminUsers());
    } catch (reason) {
      setError(toUsersError(reason));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const update = useCallback(async (userId: number, changes: AdminUserUpdate) => {
    setSavingUserIds((current) => new Set(current).add(userId));
    setError(null);
    try {
      const saved = await updateAdminUser(userId, changes);
      setUsers((current) => current?.map((user) => user.id === saved.id ? saved : user) ?? null);
      return saved;
    } catch (reason) {
      setError(toUsersError(reason));
      throw reason;
    } finally {
      setSavingUserIds((current) => {
        const next = new Set(current);
        next.delete(userId);
        return next;
      });
    }
  }, []);

  return { users, error, savingUserIds, load, update };
}

function toUsersError(reason: unknown): UsersError {
  if (reason instanceof AdminApiError) {
    return { status: reason.status, message: reason.message };
  }
  return {
    status: null,
    message: reason instanceof Error ? reason.message : "The account request failed.",
  };
}
