import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminAuth } from "../../features/auth";
import { useUnwrappedReviews } from "../../features/unwrapped";
import { useAdminUsers } from "../../features/users";
import { UsersPage } from "./UsersPage";

vi.mock("../../features/auth", () => ({
  useAdminAuth: vi.fn(),
}));

vi.mock("../../features/users/hooks/useAdminUsers", () => ({
  useAdminUsers: vi.fn(),
}));

vi.mock("../../features/unwrapped/hooks/useUnwrappedReviews", () => ({
  useUnwrappedReviews: vi.fn(),
}));

vi.mock("../topics", () => ({ TopicsPage: () => <h1>Topics desk</h1> }));
vi.mock("../auto-post", () => ({ AutoPostPage: () => <h1>Your Say official posts</h1> }));

const accounts = [
  {
    id: 1,
    email: "john.doe@example.com",
    firstName: "John",
    lastName: "Doe",
    displayName: "John Doe",
    createdDate: "2024-01-10",
    active: true,
    accountType: "ADMIN" as const,
  },
  {
    id: 10,
    email: "riley.reader@example.com",
    firstName: "Riley",
    lastName: "Reader",
    displayName: "Riley Reader",
    createdDate: "2024-06-06",
    active: true,
    accountType: "USER" as const,
  },
  {
    id: 3,
    email: "bob.johnson@example.com",
    firstName: "Bob",
    lastName: "Johnson",
    displayName: "Bob Johnson",
    createdDate: "2024-03-20",
    active: false,
    accountType: "USER" as const,
  },
];

const unwrappedReview = {
  storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f",
  postId: 42,
  milestone: 100,
  canonicalVoteCount: 126,
  status: "DRAFT" as const,
  generatedAt: "2026-07-28T10:00:00Z",
  notice: "This analysis describes people who voted on this post; it is not a population survey.",
  options: [{ id: 71, label: "Reduce public spending", ordinal: 0, semanticKey: "AGREE" }],
  argumentPages: [{
      optionId: 71,
      headline: "Why younger adults favour reducing public spending",
      selectedCohortIds: ["ageRange=AGE_25_34"],
      paragraphs: [{
        text: "Younger adults are likely to favour lower spending because deductions squeeze already stretched budgets, making a visible reduction feel more urgent than benefits promised later.",
        sourceIds: ["source-1"],
      }, {
        text: "Official figures show how the trade-off has changed over time. For these voters, immediate take-home pay can feel more valuable than distant benefits that are harder to see.",
        sourceIds: ["source-1"],
      }],
      caveat: "This association describes only people who voted on this post and does not represent any broader population.",
      sources: [{
        id: "source-1",
        url: "https://www.ons.gov.uk/data",
        publisher: "Office for National Statistics",
        title: "Public data",
        classification: "OFFICIAL" as const,
      }],
    }],
};

const unwrappedPost = {
  postId: 42,
  summary: "A measured summary of the proposal.",
  question: "Should the city introduce a workplace parking levy?",
  caseFor: "It could reduce congestion.",
  caseAgainst: "It could increase costs.",
  jurisdiction: "UNITED_KINGDOM",
  votingType: "BINARY" as const,
  createdAt: "2026-07-27T09:00:00Z",
  canonicalVoteCount: 125,
  overall: [
    { optionId: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE", count: 75, percentage: 60 },
    { optionId: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE", count: 50, percentage: 40 },
  ],
};

describe("UsersPage", () => {
  const update = vi.fn();
  const load = vi.fn();
  const logout = vi.fn();
  const login = vi.fn();
  const generate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    generate.mockResolvedValue({ postId: 42, status: "RECONCILIATION_QUEUED" });
    vi.mocked(useAdminAuth).mockReturnValue({
      status: "authenticated",
      identity: { email: "john.doe@example.com", name: "John Doe" },
      error: null,
      login,
      logout,
    });
    vi.mocked(useAdminUsers).mockReturnValue({
      users: accounts,
      error: null,
      savingUserIds: new Set(),
      load,
      update,
    });
    vi.mocked(useUnwrappedReviews).mockReturnValue({
      reviews: [unwrappedReview],
      error: null,
      actingStoryId: null,
      generatingPostId: null,
      generationError: null,
      generationMonitor: {
        workerAvailable: true,
        refreshedAt: "2026-07-28T10:01:00Z",
        statuses: [],
      },
      posts: [unwrappedPost],
      postsError: null,
      load: vi.fn(),
      loadPosts: vi.fn(),
      approve: vi.fn(),
      reject: vi.fn(),
      generate,
    });
    update.mockImplementation(async (userId, changes) => ({
      ...accounts.find((account) => account.id === userId)!,
      ...changes,
    }));
  });

  it("shows account totals and combines text, type and activity filters", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    expect(screen.getByText("Showing 3 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByLabelText("Account totals")).toHaveTextContent(
      /All\s*3\s*Official\s*0\s*Admins\s*1\s*Inactive\s*1/,
    );
    expect(screen.getByText("Riley Reader")).toBeInTheDocument();
    expect(screen.getByText("Bob Johnson")).toBeInTheDocument();
    expect(screen.getByText("riley.reader@example.com")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Find an account"), "riley");

    expect(screen.getByText("Showing 1 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByText("Riley Reader")).toBeInTheDocument();
    expect(screen.queryByText("Bob Johnson")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Find an account"));
    await user.selectOptions(screen.getByLabelText("Type"), "USER");
    expect(screen.getByText("Showing 2 of 3 accounts")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Status"), "INACTIVE");
    expect(screen.getByText("Showing 1 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByText("Bob Johnson")).toBeInTheDocument();
    expect(screen.queryByText("Riley Reader")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Type"), "ADMIN");
    expect(screen.getByText("No accounts match these filters.")).toBeInTheDocument();
  });

  it("lets an admin promote and deactivate an account before saving", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    await user.selectOptions(screen.getByLabelText("Account type for Riley Reader"), "ADMIN");
    await user.click(screen.getByLabelText("Account active for Riley Reader"));
    await user.click(screen.getAllByRole("button", { name: "Save changes" })[0]);

    expect(update).toHaveBeenCalledWith(10, {
      accountType: "ADMIN",
      active: false,
    });
  });

  it("switches from accounts to the Unwrapped publication queue", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    expect(screen.getByRole("tab", { name: /Accounts/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByLabelText("1 drafts awaiting review")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Unwrapped/ }));

    expect(screen.getByRole("tab", { name: /Unwrapped/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Unwrapped desk" })).toBeInTheDocument();
    expect(await screen.findByRole("heading", {
      name: "Why younger adults favour reducing public spending",
    }))
      .toBeInTheDocument();
    expect(screen.queryByText(unwrappedReview.notice)).not.toBeInTheDocument();
    expect(screen.queryByText(unwrappedReview.argumentPages[0].caveat)).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: unwrappedPost.question })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Run analysis for post 42" }));
    expect(generate).toHaveBeenCalledWith(42);

    await user.click(screen.getByRole("button", { name: "Generate benchmarking for post 42" }));
    expect(screen.getByRole("heading", { name: "Change the voice. Keep the evidence." }))
      .toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to Unwrapped desk" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Accounts desk" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Back to Unwrapped desk" }));
    expect(screen.getByRole("heading", { name: "Unwrapped desk" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: unwrappedPost.question })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Change the voice. Keep the evidence." }))
      .not.toBeInTheDocument();
  });

  it("switches to the topic catalogue without losing the admin masthead", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    await user.click(screen.getByRole("tab", { name: /Topics/ }));
    expect(screen.getByRole("tab", { name: /Topics/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Topics desk" })).toBeInTheDocument();
    expect(screen.getByLabelText("Your Say News")).toBeInTheDocument();
  });

  it("opens the official-post workflow from its own admin tab", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    await user.click(screen.getByRole("tab", { name: /Official posts/ }));

    expect(screen.getByRole("tab", { name: /Official posts/ }))
      .toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Your Say official posts" })).toBeInTheDocument();
    expect(screen.getByLabelText("Your Say News")).toBeInTheDocument();
  });

  it("shows a restricted state when the database account is not an active admin", () => {
    vi.mocked(useAdminUsers).mockReturnValue({
      users: null,
      error: { status: 403, message: "You are not allowed to perform this action." },
      savingUserIds: new Set(),
      load,
      update,
    });

    render(<UsersPage />);

    expect(screen.getByRole("heading", { name: "This desk is for active admins." })).toBeInTheDocument();
    expect(screen.queryByText("Accounts desk")).not.toBeInTheDocument();
  });
});
