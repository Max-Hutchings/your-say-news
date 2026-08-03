import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UnwrappedReviewDesk } from "./UnwrappedReviewDesk";

const review = {
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
        text: "Younger adults are likely to favour lower spending because current deductions squeeze already stretched budgets, making a visible reduction feel more urgent than benefits promised later.",
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

const analysisPost = {
  postId: 42,
  summary: "A measured summary of the proposal and its likely effects.",
  question: "Should the city introduce a workplace parking levy?",
  caseFor: "It could reduce congestion and fund public transport.",
  caseAgainst: "It could increase costs for workers and employers.",
  jurisdiction: "UNITED_KINGDOM",
  votingType: "BINARY" as const,
  createdAt: "2026-07-27T09:00:00Z",
  canonicalVoteCount: 125,
  overall: [
    { optionId: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE", count: 75, percentage: 60 },
    { optionId: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE", count: 50, percentage: 40 },
  ],
};

const generationMonitor = {
  workerAvailable: true,
  refreshedAt: "2026-07-28T10:01:00Z",
  statuses: [],
};

describe("UnwrappedReviewDesk", () => {
  it("shows the full proof and approves the selected publication", async () => {
    const user = userEvent.setup();
    const approve = vi.fn().mockResolvedValue({ ...review, status: "APPROVED" });

    render(
      <UnwrappedReviewDesk
        reviews={[review]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={generationMonitor}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={approve}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
        onBenchmark={vi.fn()}
      />,
    );

    expect(await screen.findByRole("heading", { name: review.argumentPages[0].headline }))
      .toBeInTheDocument();
    expect(screen.getByText("THE CASE FOR")).toBeInTheDocument();
    expect(screen.getByText("Reduce public spending")).toBeInTheDocument();
    expect(screen.queryByText(review.notice)).not.toBeInTheDocument();
    expect(screen.getByText(review.argumentPages[0].paragraphs[0].text)).toBeInTheDocument();
    expect(screen.getByText(review.argumentPages[0].paragraphs[1].text)).toBeInTheDocument();
    expect(screen.getAllByText("[1]")).toHaveLength(2);
    expect(screen.queryByText(/Observed:/)).not.toBeInTheDocument();
    expect(screen.queryByText("Wider context")).not.toBeInTheDocument();
    expect(screen.queryByText(review.argumentPages[0].caveat)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Public data" })).toHaveAttribute(
      "href",
      "https://www.ons.gov.uk/data",
    );
    expect(screen.getByText("Office for National Statistics · OFFICIAL")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Approve publication" }));

    expect(approve).toHaveBeenCalledWith(review.storyId);
  });

  it("requires and submits a reason when returning a draft", async () => {
    const user = userEvent.setup();
    const reject = vi.fn().mockResolvedValue({ ...review, status: "REJECTED" });

    render(
      <UnwrappedReviewDesk
        reviews={[review]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={generationMonitor}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={reject}
        onGenerate={vi.fn()}
        onBenchmark={vi.fn()}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Return for changes" }));
    const returnButton = screen.getByRole("button", { name: "Return draft" });
    expect(returnButton).toBeDisabled();

    await user.type(
      screen.getByLabelText("Reason for returning this draft"),
      "The second claim needs a primary source.",
    );
    await user.click(returnButton);

    expect(reject).toHaveBeenCalledWith(
      review.storyId,
      "The second claim needs a primary source.",
    );
  });

  it("shows post details and vote split, then queues analysis for that post", async () => {
    const user = userEvent.setup();
    const generate = vi.fn().mockResolvedValue({
      postId: 42,
      status: "RECONCILIATION_QUEUED",
    });
    const benchmark = vi.fn();

    render(
      <UnwrappedReviewDesk
        reviews={[]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={generationMonitor}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={generate}
        onBenchmark={benchmark}
      />,
    );

    expect(screen.getByRole("heading", { name: analysisPost.question })).toBeInTheDocument();
    expect(screen.getByText(analysisPost.summary)).toBeInTheDocument();
    expect(screen.getByLabelText("Post analysis totals")).toHaveTextContent(
      /Recent posts\s*1\s*Ready at 100\+\s*1\s*Votes shown\s*125/,
    );
    expect(screen.getByLabelText("Agree: 60%, 75 votes; Disagree: 40%, 50 votes"))
      .toBeInTheDocument();

    const button = screen.getByRole("button", { name: "Run analysis for post 42" });
    await user.click(button);

    expect(generate).toHaveBeenCalledWith(42);

    await user.click(screen.getByRole("button", { name: "Generate benchmarking for post 42" }));
    expect(benchmark).toHaveBeenCalledWith(analysisPost);
  });

  it("shows persistent queued progress and explains when generation is paused", () => {
    render(
      <UnwrappedReviewDesk
        reviews={[]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={{
          workerAvailable: false,
          refreshedAt: "2026-07-28T10:01:00Z",
          statuses: [{
            postId: 42,
            state: "QUEUED",
            queuedJobs: 1,
            generatingJobs: 0,
            readyJobs: 0,
            failedJobs: 0,
            updatedAt: "2026-07-28T10:00:00Z",
            errorMessage: null,
          }],
        }}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
        onBenchmark={vi.fn()}
      />,
    );

    expect(screen.getByText("Generation paused — API key unavailable")).toBeInTheDocument();
    expect(screen.getByText("Queued for analysis")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run analysis for post 42" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("Progress updates automatically");
  });

  it("allows an administrator to retry a failed generation", async () => {
    const user = userEvent.setup();
    const generate = vi.fn().mockResolvedValue({
      postId: 42,
      status: "RECONCILIATION_QUEUED",
    });

    render(
      <UnwrappedReviewDesk
        reviews={[]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={{
          workerAvailable: true,
          refreshedAt: "2026-07-28T10:01:00Z",
          statuses: [{
            postId: 42,
            state: "FAILED",
            queuedJobs: 0,
            generatingJobs: 0,
            readyJobs: 0,
            failedJobs: 1,
            updatedAt: "2026-07-28T10:00:00Z",
            errorMessage: "Pepper could not build this story.",
          }],
        }}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={generate}
        onBenchmark={vi.fn()}
      />,
    );

    const retry = screen.getByRole("button", { name: "Retry analysis for post 42" });
    expect(retry).toBeEnabled();
    await user.click(retry);

    expect(generate).toHaveBeenCalledWith(42);
  });

  it("pins the first milestone boundary and filters the post ledger", async () => {
    const user = userEvent.setup();
    const belowMilestone = {
      ...analysisPost,
      postId: 41,
      question: "Should library opening hours be extended?",
      canonicalVoteCount: 99,
      overall: analysisPost.overall.map((option) => ({ ...option, count: option.ordinal === 0 ? 59 : 40 })),
    };
    const atMilestone = {
      ...analysisPost,
      canonicalVoteCount: 100,
      overall: analysisPost.overall.map((option) => ({ ...option, count: option.ordinal === 0 ? 60 : 40 })),
    };

    render(
      <UnwrappedReviewDesk
        reviews={[]}
        posts={[belowMilestone, atMilestone]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        generationMonitor={generationMonitor}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
        onBenchmark={vi.fn()}
      />,
    );

    expect(screen.getByText("1 vote to go")).toBeInTheDocument();
    expect(screen.getByText("Ready to analyse", { selector: "span.analysis-readiness" }))
      .toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Milestone"), "ELIGIBLE");
    expect(screen.getByRole("heading", { name: atMilestone.question })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: belowMilestone.question })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Milestone"), "ALL");
    await user.type(screen.getByLabelText("Find a post"), "library");
    expect(screen.getByRole("heading", { name: belowMilestone.question })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: atMilestone.question })).not.toBeInTheDocument();
  });
});
