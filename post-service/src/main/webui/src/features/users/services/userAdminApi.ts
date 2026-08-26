import { adminFetch } from "../../auth";
import type { AdminUser, AdminUserUpdate } from "../types";

export class AdminApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function adminRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await adminFetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new AdminApiError(response.status, body?.message ?? "The account request failed.");
  }

  return response.json() as Promise<T>;
}

export function getAdminUsers(): Promise<AdminUser[]> {
  return adminRequest<AdminUser[]>("/api/admin/users");
}

export function updateAdminUser(userId: number, update: AdminUserUpdate): Promise<AdminUser> {
  return adminRequest<AdminUser>(`/api/admin/users/${userId}`, {
    method: "PUT",
    body: JSON.stringify(update),
  });
}
