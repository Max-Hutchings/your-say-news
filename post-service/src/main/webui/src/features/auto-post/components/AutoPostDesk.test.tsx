import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AutoPostDesk } from "./AutoPostDesk";
import type { AutoPostRun } from "../types";

const candidates = Array.from({ length: 10 }, (_, index) => ({
  id: `candidate-${index + 1}`,
  rank: index + 1,
  region: (["UK", "US", "GLOBAL"] as const)[index % 3],
  headline: index === 0
    ? "Chancellor sets out revised budget rules"
    : `Representative current story ${index + 1}`,
  summary: index === 0
    ? "The Treasury changed the fiscal rules used for the next spending period."
    : `A factual account of current story ${index + 1}.`,
  publishedAt: `2026-08-20T${String(11 - index).padStart(2, "0")}:00:00Z`,
  sources: [{
    url: `https://news.example.com/story-${index + 1}`,
    title: `Source for story ${index + 1}`,
    publisher: "Representative News",
  }],
}));

const readyRun: AutoPostRun = {
  id: "50b05ab6-a324-4fb4-bab6-e7c14bc5ce83",
  status: "CANDIDATES_READY",
  windowStart: "2026-08-19T12:00:00Z",
  windowEnd: "2026-08-20T12:00:00Z",
  candidates,
  selectedCandidateId: null,
  pepperDraftId: null,
  draft: null,
  publishedPostId: null,
  errorCode: null,
  errorMessage: null,
  createdAt: "2026-08-20T12:00:00Z",
  updatedAt: "2026-08-20T12:01:00Z",
};

const draftingRun: AutoPostRun = {
  ...readyRun,
  id: "8d724e08-1285-44c5-b8e3-fd4987f7a993",
  status: "DRAFTING",
  candidates,
  selectedCandidateId: candidates[3].id,
  pepperDraftId: "9fe1c153-23cb-4b4f-b71c-ea9326f346c6",
  publishedPostId: null,
  createdAt: "2026-08-19T12:00:00Z",
  updatedAt: "2026-08-19T12:08:00Z",
};

const draftReadyRun: AutoPostRun = {
  ...draftingRun,
  status: "DRAFT_READY",
  draft: {
    id: draftingRun.pepperDraftId!,
    summary: "A balanced publication-ready article.",
    supportQuestion: "Do you support the revised budget rules?",
    caseFor: "Supporters cite fiscal stability.",
    caseAgainst: "Opponents cite reduced flexibility.",
    votingType: "BINARY",
    voteOptions: ["Agree", "Disagree"],
    citations: candidates[0].sources,
    version: 1,
  },
};

const publishedRun: AutoPostRun = {
  ...draftReadyRun,
  status: "PUBLISHED",
  publishedPostId: 4102,
};

describe("AutoPostDesk", () => {
  it("shows the exact 24-hour window, ten ranked stories and prior workflow history", () => {
    render(<AutoPostDesk
      runs={[readyRun, draftingRun]}
      activeRun={readyRun}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onReload={vi.fn()}
    />);

    expect(screen.getByRole("heading", { name: "Your Say official posts" })).toBeInTheDocument();
    expect(screen.getByLabelText("24-hour discovery window")).toHaveTextContent(
      /19 Aug 2026, 13:00.*20 Aug 2026, 13:00/,
    );
    expect(screen.getByLabelText("Top stories from the previous 24 hours"))
      .toHaveTextContent(/1.*Chancellor sets out revised budget rules.*UK/);
    expect(screen.getAllByRole("button", { name: /^Select / })).toHaveLength(10);
    expect(screen.getAllByText("Drafting")).toHaveLength(1);
  });

  it("reveals a summary and requires confirmation before handing a story to post agent", async () => {
    const user = userEvent.setup();
    const select = vi.fn().mockResolvedValue(undefined);
    render(<AutoPostDesk
      runs={[readyRun]}
      activeRun={readyRun}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={select}
      onReload={vi.fn()}
    />);

    expect(screen.queryByText(candidates[0].summary)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: candidates[0].headline }));
    expect(screen.getByText(candidates[0].summary)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: `Select ${candidates[0].headline}` }));
    expect(select).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: "Confirm story selection" })).toHaveTextContent(
      candidates[0].headline,
    );

    await user.click(screen.getByRole("button", { name: "Confirm and create draft" }));
    expect(select).toHaveBeenCalledWith(readyRun.id, candidates[0].id);
  });

  it("starts discovery only from the explicit create action", async () => {
    const user = userEvent.setup();
    const create = vi.fn().mockResolvedValue(undefined);
    render(<AutoPostDesk
      runs={[]}
      activeRun={null}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={create}
      onSelect={vi.fn()}
      onReload={vi.fn()}
    />);

    expect(create).not.toHaveBeenCalled();
    expect(screen.getByText("No official posts have been created through this desk yet."))
      .toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Create new" }));
    expect(create).toHaveBeenCalledTimes(1);
  });

  it("shows publication success without keeping the published workflow at the top", () => {
    render(<AutoPostDesk
      runs={[publishedRun]}
      activeRun={null}
      publishedPostId={publishedRun.publishedPostId}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onReload={vi.fn()}
    />);

    expect(screen.queryByLabelText("Current official-post workflow")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Published");
    expect(screen.getByRole("status")).toHaveTextContent("Post 4102 is live");
  });

  it("requests and displays the full run when any history row is clicked", async () => {
    const user = userEvent.setup();
    const viewRun = vi.fn().mockResolvedValue(undefined);
    const view = render(<AutoPostDesk
      runs={[publishedRun]}
      activeRun={null}
      viewedRun={null}
      loadingRunId={null}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onViewRun={viewRun}
      onReload={vi.fn()}
    />);

    await user.click(screen.getByRole("button", { name: /Representative current story 4/ }));
    expect(viewRun).toHaveBeenCalledWith(publishedRun.id);

    view.rerender(<AutoPostDesk
      runs={[publishedRun]}
      activeRun={null}
      viewedRun={publishedRun}
      loadingRunId={null}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onViewRun={viewRun}
      onReload={vi.fn()}
    />);
    expect(screen.getByRole("region", { name: "Loaded official post" }))
      .toHaveTextContent(publishedRun.draft!.summary);
  });

  it("locks selection after handoff and explains drafting progress", () => {
    render(<AutoPostDesk
      runs={[draftingRun]}
      activeRun={draftingRun}
      error={null}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onReload={vi.fn()}
    />);

    expect(screen.getByRole("status")).toHaveTextContent("Post agent is creating the draft");
    expect(screen.getAllByRole("button", { name: /^Select / })).toHaveLength(10);
    screen.getAllByRole("button", { name: /^Select / }).forEach((button) => {
      expect(button).toBeDisabled();
    });
  });

  it("shows discovery failure and offers a reload", async () => {
    const user = userEvent.setup();
    const reload = vi.fn().mockResolvedValue(undefined);
    const failedRun = {
      ...readyRun,
      status: "FAILED" as const,
      candidates: [],
      errorMessage: "The discovered story list did not pass validation.",
    };
    render(<AutoPostDesk
      runs={[failedRun]}
      activeRun={failedRun}
      error={{ status: 502, message: "The live update was interrupted." }}
      creating={false}
      selectingCandidateId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onReload={reload}
    />);

    expect(screen.getByText("The discovered story list did not pass validation.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(reload).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: "Retry draft" })).not.toBeInTheDocument();
  });

  it("offers retry for each failed post-agent draft in the history", async () => {
    const user = userEvent.setup();
    const retry = vi.fn().mockResolvedValue(undefined);
    const failedDraftRun: AutoPostRun = {
      ...draftingRun,
      status: "FAILED",
      errorCode: "AUTO_POST_DRAFT_FAILED",
      errorMessage: "Post agent could not create the draft.",
    };
    const view = render(<AutoPostDesk
      runs={[failedDraftRun]}
      activeRun={null}
      error={null}
      creating={false}
      selectingCandidateId={null}
      retryingRunId={null}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onRetry={retry}
      onReload={vi.fn()}
    />);

    await user.click(screen.getByRole("button", { name: "Retry draft" }));
    expect(retry).toHaveBeenCalledWith(failedDraftRun.id);

    view.rerender(<AutoPostDesk
      runs={[failedDraftRun]}
      activeRun={null}
      error={null}
      creating={false}
      selectingCandidateId={null}
      retryingRunId={failedDraftRun.id}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onRetry={retry}
      onReload={vi.fn()}
    />);
    expect(screen.getByRole("button", { name: "Retrying…" })).toBeDisabled();
  });

  it("shows the returned draft and requires final confirmation before publication", async () => {
    const user = userEvent.setup();
    const approve = vi.fn().mockResolvedValue(undefined);
    render(<AutoPostDesk
      runs={[draftReadyRun]}
      activeRun={draftReadyRun}
      error={null}
      creating={false}
      selectingCandidateId={null}
      approving={false}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onApprove={approve}
      onReload={vi.fn()}
    />);

    expect(screen.getByRole("heading", { name: "Review before publishing" })).toBeInTheDocument();
    expect(screen.getByText(draftReadyRun.draft!.summary)).toBeInTheDocument();
    expect(screen.getByText(draftReadyRun.draft!.supportQuestion)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Approve draft" }));
    expect(approve).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: "Approve and publish?" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Approve and publish" }));
    expect(approve).toHaveBeenCalledWith(draftReadyRun.id);
  });

  it("closes the approval dialog when publication fails so the error alert is visible", async () => {
    const user = userEvent.setup();
    const approve = vi.fn().mockRejectedValue(new Error("Publication failed"));
    render(<AutoPostDesk
      runs={[draftReadyRun]}
      activeRun={draftReadyRun}
      error={{ status: 502, message: "The approved post could not be published. Try again." }}
      creating={false}
      selectingCandidateId={null}
      approving={false}
      onCreate={vi.fn()}
      onSelect={vi.fn()}
      onApprove={approve}
      onReload={vi.fn()}
    />);

    await user.click(screen.getByRole("button", { name: "Approve draft" }));
    await user.click(screen.getByRole("button", { name: "Approve and publish" }));

    expect(screen.queryByRole("dialog", { name: "Approve and publish?" })).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The approved post could not be published. Try again.",
    );
  });
});
