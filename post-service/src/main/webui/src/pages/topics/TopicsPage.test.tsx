import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminTopics } from "../../features/topics";
import { TopicsPage } from "./TopicsPage";
import { canonicalTopicId } from "../../features/topics/components/AddTopicForm";

vi.mock("../../features/topics/hooks/useAdminTopics", () => ({ useAdminTopics: vi.fn() }));

const topics = [
  { id: "politics", label: "Politics", displayGroup: "Politics & government", displayOrder: 1, active: true },
  { id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true },
  { id: "old-topic", label: "Old topic", displayGroup: "Society", displayOrder: 21, active: false },
];

describe("TopicsPage", () => {
  const add = vi.fn();
  const setActive = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    add.mockResolvedValue({ id: "public-transport", label: "Public transport", displayGroup: "Society", displayOrder: 22, active: true });
    setActive.mockResolvedValue({ ...topics[1], active: false });
    vi.mocked(useAdminTopics).mockReturnValue({
      topics, error: null, savingIds: new Set(), adding: false, load: vi.fn(), add, setActive,
    });
  });

  it("shows active and retired topics, adds a canonical topic, and can retire one", async () => {
    const user = userEvent.setup();
    render(<TopicsPage />);

    expect(screen.getByLabelText("Topic totals")).toHaveTextContent(/Catalogue\s*3\s*Active\s*2\s*Retired\s*1/);
    expect(screen.getByText("old-topic")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Label"), "Public transport");
    expect(screen.getByLabelText("Canonical topic ID")).toHaveTextContent("public-transport");
    await user.selectOptions(screen.getByLabelText("Display group"), "Society");
    await user.click(screen.getByRole("button", { name: "Add topic" }));
    expect(add).toHaveBeenCalledWith({ label: "Public transport", displayGroup: "Society" });

    await user.click(screen.getByLabelText("Topic active for Housing"));
    expect(setActive).toHaveBeenCalledWith("housing", false);
  });
});

it("previews only canonical ids the backend accepts", () => {
  expect(canonicalTopicId("Café culture")).toBe("cafe-culture");
  expect(canonicalTopicId("X")).toBe("");
  expect(canonicalTopicId("!!!")).toBe("");
  expect(canonicalTopicId("b".repeat(63) + " tail")).toBe("b".repeat(63));
});
