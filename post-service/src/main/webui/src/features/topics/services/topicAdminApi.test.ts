import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({ getAccessToken: vi.fn().mockResolvedValue("admin-token") }));

import { createAdminTopic, getAdminTopics, setAdminTopicActive } from "./topicAdminApi";

const housing = { id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true };

describe("topicAdminApi", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("lists, creates and retires topics through the admin endpoints", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([housing]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...housing, id: "public-transport", label: "Public transport", displayOrder: 21 }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...housing, active: false }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getAdminTopics()).resolves.toEqual([housing]);
    await createAdminTopic({ label: "Public transport", displayGroup: "Transport & places" });
    await setAdminTopicActive("housing", false);

    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/admin/topics", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ label: "Public transport", displayGroup: "Transport & places" }),
      headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/admin/topics/housing/active", expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ active: false }),
    }));
  });
});
