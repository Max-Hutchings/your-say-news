import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  generateUnwrappedBenchmark,
  getUnwrappedBenchmarkPrompt,
} from "../services/unwrappedAdminApi";
import { UnwrappedBenchmarkPage } from "./UnwrappedBenchmarkPage";

vi.mock("../services/unwrappedAdminApi", async (importOriginal) => ({
  ...await importOriginal<typeof import("../services/unwrappedAdminApi")>(),
  getUnwrappedBenchmarkPrompt: vi.fn(),
  generateUnwrappedBenchmark: vi.fn(),
}));

const post = {
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

const articlePage = {
  optionId: 71,
  headline: "Commuters weigh the cost of cleaner streets",
  selectedCohortIds: ["ageRange=AGE_25_34"],
  paragraphs: [{
    text: "Younger commuters are likely to favour the levy because reliable public transport can reduce the cost and unpredictability of travelling to work.",
    sourceIds: ["source-1"],
  }],
  caveat: "This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.",
  sources: [{
    id: "source-1",
    url: "https://www.ons.gov.uk/transport",
    publisher: "Office for National Statistics",
    title: "Transport data",
    classification: "OFFICIAL" as const,
  }],
};

function benchmarkResponse(
  systemPrompt: string,
  headline = articlePage.headline,
  attemptCount = 1,
  effectiveSystemPrompt = systemPrompt,
) {
  return {
    postId: 42,
    generatedAt: "2026-08-03T12:00:00Z",
    options: [
      { id: 71, label: "Support the levy", ordinal: 0, semanticKey: "AGREE" },
      { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
    ],
    variants: [{
      position: 1,
      systemPrompt,
      effectiveSystemPrompt,
      attemptCount,
      status: "SUCCEEDED" as const,
      model: "grok-test",
      providerResponseId: `response-${systemPrompt.toLowerCase().replaceAll(" ", "-")}`,
      argumentPages: [{ ...articlePage, headline }],
      errorCode: null,
      errorMessage: null,
    }],
  };
}

function failedBenchmarkResponse(systemPrompt: string, errorMessage: string) {
  return {
    postId: 42,
    generatedAt: "2026-08-03T12:00:00Z",
    options: [
      { id: 71, label: "Support the levy", ordinal: 0, semanticKey: "AGREE" },
      { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
    ],
    variants: [{
      position: 1,
      systemPrompt,
      effectiveSystemPrompt: systemPrompt,
      attemptCount: 1,
      status: "FAILED" as const,
      model: null,
      providerResponseId: null,
      argumentPages: [],
      errorCode: "UNWRAPPED_INVALID_PROVIDER_RESPONSE",
      errorMessage,
    }],
  };
}

describe("UnwrappedBenchmarkPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getUnwrappedBenchmarkPrompt).mockResolvedValue({
      systemPrompt: "Production system prompt",
    });
    vi.mocked(generateUnwrappedBenchmark).mockImplementation(
      async (_postId, [systemPrompt]) => benchmarkResponse(systemPrompt),
    );
  });

  it("generates and iterates lanes independently without clearing other results", async () => {
    const user = userEvent.setup();
    const onBack = vi.fn();
    render(<UnwrappedBenchmarkPage post={post} onBack={onBack} />);

    expect(screen.getByRole("heading", { name: post.question })).toBeInTheDocument();
    expect(screen.getByText(post.summary)).toBeInTheDocument();
    expect(screen.getByLabelText("Agree: 60%, 75 votes; Disagree: 40%, 50 votes"))
      .toBeInTheDocument();

    const editors = await screen.findAllByDisplayValue("Production system prompt");
    expect(editors).toHaveLength(3);
    for (const [index, value] of ["Prompt A", "Prompt B", "Prompt C"].entries()) {
      await user.clear(editors[index]);
      await user.type(editors[index], value);
    }

    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));

    expect(generateUnwrappedBenchmark).toHaveBeenNthCalledWith(1, 42, ["Prompt A"]);
    expect(await screen.findByRole("heading", { name: articlePage.headline }))
      .toBeInTheDocument();
    expect(screen.getByText("Support the levy")).toBeInTheDocument();
    expect(screen.getByText(articlePage.paragraphs[0].text)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Transport data" }))
      .toHaveAttribute("href", "https://www.ons.gov.uk/transport");
    expect(screen.getByText("1 of 3 comparisons generated")).toBeInTheDocument();
    expect(screen.getByLabelText("Result B")).toHaveTextContent(
      "The generated article will continue down this lane.",
    );

    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(benchmarkResponse(
      "Prompt B",
      "Workers compare roads with reliable buses",
    ));
    await user.click(screen.getByRole("button", { name: "Generate prompt B" }));

    expect(generateUnwrappedBenchmark).toHaveBeenNthCalledWith(2, 42, ["Prompt B"]);
    expect(screen.getByRole("heading", { name: articlePage.headline })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Workers compare roads with reliable buses" }))
      .toBeInTheDocument();
    expect(screen.getByText("2 of 3 comparisons generated")).toBeInTheDocument();

    await user.clear(editors[0]);
    await user.type(editors[0], "Prompt A revised");
    expect(screen.getByText("Prompt edited · result preserved")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: articlePage.headline })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Workers compare roads with reliable buses" }))
      .toBeInTheDocument();

    let resolveRerun!: (value: ReturnType<typeof benchmarkResponse>) => void;
    vi.mocked(generateUnwrappedBenchmark).mockReturnValueOnce(new Promise((resolve) => {
      resolveRerun = resolve;
    }));
    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));
    expect(screen.getByRole("status")).toHaveTextContent(
      "Generating a new result; the current comparison stays visible.",
    );
    expect(screen.getByRole("heading", { name: articlePage.headline })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Workers compare roads with reliable buses" }))
      .toBeInTheDocument();

    await act(async () => resolveRerun(benchmarkResponse(
      "Prompt A revised",
      "Drivers reassess the price of crowded roads",
    )));
    expect(await screen.findByRole("heading", {
      name: "Drivers reassess the price of crowded roads",
    })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: articlePage.headline })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Workers compare roads with reliable buses" }))
      .toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Back to Unwrapped desk" }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it("keeps editors disabled while the production prompt is loading", () => {
    vi.mocked(getUnwrappedBenchmarkPrompt).mockReturnValue(new Promise(() => undefined));

    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    expect(screen.getAllByPlaceholderText("Loading the production prompt…")).toHaveLength(3);
    for (const editor of screen.getAllByRole("textbox")) {
      expect(editor).toBeDisabled();
    }
    expect(screen.getByRole("button", { name: "Generate prompt A" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Generate prompt B" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Generate prompt C" })).toBeDisabled();
  });

  it("shows the effective repair prompt when a lane needs multiple attempts", async () => {
    const user = userEvent.setup();
    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(benchmarkResponse(
      "Production system prompt",
      articlePage.headline,
      3,
      "Production system prompt\n\nValidation repair instructions",
    ));
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    await screen.findAllByDisplayValue("Production system prompt");
    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));

    expect(await screen.findByText("Completed · 3 attempts")).toBeInTheDocument();
    const disclosure = screen.getByText("3 attempts · view effective repair prompt");
    await user.click(disclosure);
    expect(screen.getByText(/Validation repair instructions/)).toBeInTheDocument();
  });

  it("reports prompt loading and generation failures and rejects whitespace prompts", async () => {
    const user = userEvent.setup();
    vi.mocked(getUnwrappedBenchmarkPrompt).mockRejectedValueOnce(
      new Error("Production prompt could not be loaded."),
    );
    const { unmount } = render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Production prompt could not be loaded.",
    );
    unmount();

    vi.mocked(getUnwrappedBenchmarkPrompt).mockResolvedValue({ systemPrompt: "Production prompt" });
    vi.mocked(generateUnwrappedBenchmark).mockRejectedValue(
      new Error("The provider timed out."),
    );
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);
    const editors = await screen.findAllByDisplayValue("Production prompt");
    await user.clear(editors[1]);
    await user.type(editors[1], "   ");
    expect(screen.getByRole("button", { name: "Generate prompt B" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Generate prompt A" })).toBeEnabled();
    expect(generateUnwrappedBenchmark).not.toHaveBeenCalled();

    await user.clear(editors[1]);
    await user.type(editors[1], "Valid replacement prompt");
    await user.click(screen.getByRole("button", { name: "Generate prompt B" }));
    expect(generateUnwrappedBenchmark).toHaveBeenCalledWith(42, ["Valid replacement prompt"]);
    expect(await screen.findByRole("alert")).toHaveTextContent("The provider timed out.");
  });

  it("shows an initial failed variant and preserves a successful result after a failed rerun", async () => {
    const user = userEvent.setup();
    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(failedBenchmarkResponse(
      "Production system prompt",
      "The provider returned an invalid article structure.",
    ));
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    await screen.findAllByDisplayValue("Production system prompt");
    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The provider returned an invalid article structure.",
    );
    expect(screen.getByText("UNWRAPPED_INVALID_PROVIDER_RESPONSE")).toBeInTheDocument();

    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(benchmarkResponse(
      "Production system prompt",
      "A valid comparison worth preserving",
    ));
    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));
    expect(await screen.findByRole("heading", { name: "A valid comparison worth preserving" }))
      .toBeInTheDocument();

    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(failedBenchmarkResponse(
      "Production system prompt",
      "The revised prompt did not produce a valid draft.",
    ));
    await user.click(screen.getByRole("button", { name: "Generate prompt A" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The revised prompt did not produce a valid draft.",
    );
    expect(screen.getByRole("heading", { name: "A valid comparison worth preserving" }))
      .toBeInTheDocument();
    expect(screen.queryByText("UNWRAPPED_INVALID_PROVIDER_RESPONSE")).not.toBeInTheDocument();
  });
});
