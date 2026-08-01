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
  draft: {
    pages: [{
      optionId: 71,
      headline: "The case for changing course",
      usedCohortIds: ["ageRange=AGE_25_34"],
      contextClaims: [{
        id: "claim-1",
        statement: "Official figures show a material change.",
        sourceIds: ["source-1"],
        interpretation: false,
      }],
      synthesis: "Taken together, the evidence makes a serious case.",
      caveat: "This sample is an association and does not prove individual motivation.",
    }],
    sources: [{
      id: "source-1",
      url: "https://www.ons.gov.uk/data",
      publisher: "Office for National Statistics",
      title: "Public data",
      classification: "OFFICIAL" as const,
    }],
  },
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
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={approve}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
      />,
    );

    expect(await screen.findByRole("heading", { name: review.draft.pages[0].headline }))
      .toBeInTheDocument();
    expect(screen.getByText("Official figures show a material change.")).toBeInTheDocument();
    expect(screen.getByText("Observed: ageRange=AGE_25_34")).toBeInTheDocument();
    expect(screen.getByText(review.draft.pages[0].synthesis)).toBeInTheDocument();
    expect(screen.getByText(review.draft.pages[0].caveat)).toBeInTheDocument();
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
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={reject}
        onGenerate={vi.fn()}
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

    render(
      <UnwrappedReviewDesk
        reviews={[]}
        posts={[analysisPost]}
        postsError={null}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={generate}
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
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Milestone check queued",
    );
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
        onReload={vi.fn()}
        onReloadPosts={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
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
