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

describe("UnwrappedReviewDesk", () => {
  it("shows the full proof and approves the selected publication", async () => {
    const user = userEvent.setup();
    const approve = vi.fn().mockResolvedValue({ ...review, status: "APPROVED" });

    render(
      <UnwrappedReviewDesk
        reviews={[review]}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        onReload={vi.fn()}
        onApprove={approve}
        onReject={vi.fn()}
        onGenerate={vi.fn()}
      />,
    );

    expect(await screen.findByRole("heading", { name: review.draft.pages[0].headline }))
      .toBeInTheDocument();
    expect(screen.getByText("Official figures show a material change.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Public data" })).toHaveAttribute(
      "href",
      "https://www.ons.gov.uk/data",
    );

    await user.click(screen.getByRole("button", { name: "Approve publication" }));

    expect(approve).toHaveBeenCalledWith(review.storyId);
  });

  it("requires and submits a reason when returning a draft", async () => {
    const user = userEvent.setup();
    const reject = vi.fn().mockResolvedValue({ ...review, status: "REJECTED" });

    render(
      <UnwrappedReviewDesk
        reviews={[review]}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        onReload={vi.fn()}
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

  it("queues a forced run by post ID and explains where the draft will appear", async () => {
    const user = userEvent.setup();
    const generate = vi.fn().mockResolvedValue({
      jobId: "29e798a2-90d8-4407-bd5e-92e31afc77cb",
      postId: 42,
      milestone: 18,
      status: "PENDING",
      created: true,
    });

    render(
      <UnwrappedReviewDesk
        reviews={[]}
        error={null}
        actingStoryId={null}
        generatingPostId={null}
        generationError={null}
        onReload={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onGenerate={generate}
      />,
    );

    const button = screen.getByRole("button", { name: "Force generation" });
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("Post ID"), "42");
    await user.click(button);

    expect(generate).toHaveBeenCalledWith(42);
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Generation queued for post 42. Refresh the queue when the draft is ready.",
    );
  });
});
