import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  generateUnwrappedBenchmark,
  getUnwrappedBenchmarkPrompt,
} from "../services/unwrappedAdminApi";
import type { UnwrappedBenchmarkPrompt } from "../types";
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

const benchmarkContext: UnwrappedBenchmarkPrompt = {
  systemPrompt: "Production system prompt",
  outputInstructions: "Return exactly 2 pages in option order [71, 72].",
  input: {
    postId: 42,
    summary: post.summary,
    question: post.question,
    jurisdiction: post.jurisdiction,
    canonicalVoteCount: 125,
    aggregateVersion: "aggregate-v1",
    options: [{
      option: { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
      overallVoteCount: 75,
      overallVotePercentage: 60,
      candidates: [{
        cohortId: "ageRange=AGE_25_34|gender=MAN",
        dimensions: [
          { axis: "ageRange", bucket: "AGE_25_34" },
          { axis: "gender", bucket: "MAN" },
        ],
        role: "INTERSECTION_DISCOVERY",
        relevanceReason: "Strongest non-redundant two-characteristic intersection.",
        sampleSize: 40,
        populationSharePercentage: 32,
        optionVoteCount: 32,
        compositionPercentage: 42.7,
        propensityPercentage: 80,
        overIndexPercentagePoints: 20,
        differenceFromRestPercentagePoints: 29.4,
        wilson95Low: 65.2,
        wilson95High: 89.5,
        adjustedQValue: 0.004,
        displayName: "Men aged 25 to 34",
      }, {
        cohortId: "householdIncomeRange=income|GB-GBP-GROSS-2025-v1|HOUSEHOLD|HOUSEHOLD_TIER_7",
        dimensions: [{
          axis: "householdIncomeRange",
          bucket: "income|GB-GBP-GROSS-2025-v1|HOUSEHOLD|HOUSEHOLD_TIER_7",
          label: "GBP 200k or more",
          income: {
            bucketId: "income|GB-GBP-GROSS-2025-v1|HOUSEHOLD|HOUSEHOLD_TIER_7",
            label: "GBP 200k or more",
            contextLabel: "Annual household income before tax in the United Kingdom",
            relativeLabel: "Top 5% locally",
            marketCode: "GB",
            marketLabel: "United Kingdom",
            currencyCode: "GBP",
            measure: "HOUSEHOLD",
            measureLabel: "Annual household income before tax",
            lowerInclusive: 200_000,
            upperExclusive: null,
            relativeTier: "TIER_7",
            profileId: "GB-GBP-GROSS-2025-v1",
            profileVersion: 1,
            bandId: "HOUSEHOLD_TIER_7",
          },
        }],
        role: "CORE_DIFFERENTIATOR",
        relevanceReason: "Strongest available core differentiator for this option.",
        sampleSize: 20,
        populationSharePercentage: 16,
        optionVoteCount: 16,
        compositionPercentage: 21.3,
        propensityPercentage: 75,
        overIndexPercentagePoints: 15,
        differenceFromRestPercentagePoints: 23.8,
        wilson95Low: 58.4,
        wilson95High: 91.9,
        adjustedQValue: 0.009,
        displayName: "People with annual household income of GBP 200k or more in the United Kingdom",
      }],
      narrativeInstructions: [
        "Explain why a selected cohort is likely to favour the option using researched context.",
      ],
      insufficientEvidence: null,
    }, {
      option: { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
      overallVoteCount: 50,
      overallVotePercentage: 40,
      candidates: [],
      narrativeInstructions: [
        "Do not introduce a cohort absent from this shortlist.",
      ],
      insufficientEvidence: "No reliable demographic concentration passes the narration rules.",
    }],
  },
};

const articlePage = {
  optionId: 71,
  headline: "Commuters weigh the cost of cleaner streets",
  selectedCohortIds: ["ageRange=AGE_25_34"],
  paragraphs: [{
    text: "**Younger commuters**\n\nThis group may favour the levy because reliable public transport can reduce commuting costs.\n\n- More predictable journeys\n- **Lower travel costs**",
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

function benchmarkResponseForPrompts(
  systemPrompts: string[],
  headlines = [
    "Drivers reconsider the daily cost of congestion",
    "Employers weigh parking costs against cleaner air",
    "Bus passengers compare reliability with road delays",
  ],
) {
  const response = benchmarkResponse(systemPrompts[0]);
  return {
    ...response,
    variants: systemPrompts.map((systemPrompt, index) => ({
      ...benchmarkResponse(systemPrompt, headlines[index]).variants[0],
      position: index + 1,
    })),
  };
}

describe("UnwrappedBenchmarkPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getUnwrappedBenchmarkPrompt).mockResolvedValue(benchmarkContext);
    vi.mocked(generateUnwrappedBenchmark).mockImplementation(
      async (_postId, [systemPrompt]) => benchmarkResponse(systemPrompt),
    );
  });

  it("shows the exact LLM evidence and characteristic groups before generation", async () => {
    const user = userEvent.setup();
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    const contextHeading = await screen.findByRole("heading", { name: "What the model receives" });
    const contextSection = contextHeading.closest("section");
    expect(contextSection).not.toBeNull();
    const contextView = within(contextSection!);
    expect(contextView.getByText("Snapshot").nextElementSibling).toHaveTextContent("aggregate-v1");
    expect(contextView.getByText("Options").nextElementSibling).toHaveTextContent("2");
    expect(contextView.getByText("Votes").nextElementSibling).toHaveTextContent("125");
    expect(getUnwrappedBenchmarkPrompt).toHaveBeenCalledWith(42);
    const agreeOption = contextView.getByRole("heading", { name: "Agree" }).closest("article");
    expect(agreeOption).toHaveTextContent("75 votes");
    expect(agreeOption).toHaveTextContent("60%");
    const disagreeOption = contextView.getByRole("heading", { name: "Disagree" }).closest("article");
    expect(disagreeOption).toHaveTextContent("50 votes");
    expect(disagreeOption).toHaveTextContent("40%");
    const ageHeading = screen.getByRole("heading", { name: "Men aged 25 to 34" });
    const ageCard = within(ageHeading.closest("section")!);
    expect(ageCard.getByText("Intersection discovery")).toBeInTheDocument();
    expect(ageCard.getByText("ageRange=AGE_25_34|gender=MAN")).toBeInTheDocument();
    expect(ageCard.getByText("Age range · Age 25 34")).toBeInTheDocument();
    expect(ageCard.getByText("Gender · Man")).toBeInTheDocument();
    expect(ageCard.getByText("40 voters in group")).toBeInTheDocument();
    expect(ageCard.getByText("32 chose Agree")).toBeInTheDocument();
    expect(ageCard.getByText("80% chose Agree")).toBeInTheDocument();
    expect(ageCard.getByText("+29.4pp vs everyone else")).toBeInTheDocument();
    expect(ageCard.getByText("32% of post voters")).toBeInTheDocument();
    expect(ageCard.getByText("42.7% of Agree voters")).toBeInTheDocument();
    expect(ageCard.getByText("+20pp")).toBeInTheDocument();
    expect(ageCard.getByText("65.2%–89.5%")).toBeInTheDocument();
    expect(ageCard.getByText("0.004")).toBeInTheDocument();
    expect(ageCard.getByText("Strongest non-redundant two-characteristic intersection."))
      .toBeInTheDocument();
    const incomeHeading = screen.getByRole("heading", {
      name: "People with annual household income of GBP 200k or more in the United Kingdom",
    });
    const incomeCard = within(incomeHeading.closest("section")!);
    expect(incomeCard.getByText(
      "Annual household income before tax · United Kingdom · GBP 200k or more",
    )).toBeInTheDocument();
    expect(screen.queryByText(
      "Household income range · Income|gb-gbp-gross-2025-v1|household|household tier 7",
    )).not.toBeInTheDocument();
    expect(screen.getByText("No characteristic groups supplied")).toBeInTheDocument();
    expect(screen.getByText("No reliable demographic concentration passes the narration rules."))
      .toBeInTheDocument();
    expect(screen.getByText(
      "Explain why a selected cohort is likely to favour the option using researched context.",
    )).toBeInTheDocument();
    expect(screen.getByText("Do not introduce a cohort absent from this shortlist."))
      .toBeInTheDocument();

    await user.click(screen.getByText("Fixed output instructions"));
    expect(screen.getByText("Return exactly 2 pages in option order [71, 72]."))
      .toBeInTheDocument();
    await user.click(screen.getByText("Raw input JSON"));
    const rawInput = screen.getByText("Raw input JSON").closest("details")?.querySelector("pre");
    expect(rawInput).toHaveTextContent(JSON.stringify(benchmarkContext.input, null, 2), {
      normalizeWhitespace: false,
    });
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
    expect(screen.getByText("Younger commuters").tagName).toBe("STRONG");
    expect(screen.getByText("More predictable journeys").closest("li")).toBeInTheDocument();
    expect(screen.getByText("Lower travel costs").tagName).toBe("STRONG");
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

  it("submits all three prompts in one request and assigns each returned variant to its lane", async () => {
    const user = userEvent.setup();
    let resolveBenchmark!: (value: ReturnType<typeof benchmarkResponseForPrompts>) => void;
    vi.mocked(generateUnwrappedBenchmark).mockReturnValueOnce(new Promise((resolve) => {
      resolveBenchmark = resolve;
    }));
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    const editors = await screen.findAllByDisplayValue("Production system prompt");
    const systemPrompts = ["Prompt A", "Prompt B", "Prompt C"];
    for (const [index, systemPrompt] of systemPrompts.entries()) {
      await user.clear(editors[index]);
      await user.type(editors[index], systemPrompt);
    }

    await user.click(screen.getByRole("button", { name: "Generate all three" }));

    expect(generateUnwrappedBenchmark).toHaveBeenCalledOnce();
    expect(generateUnwrappedBenchmark).toHaveBeenCalledWith(42, systemPrompts);
    expect(screen.getByRole("button", { name: "Generating all three…" })).toBeDisabled();
    expect(screen.getAllByText(/Generating comparison [ABC]…/)).toHaveLength(3);
    for (const editor of editors) expect(editor).toBeDisabled();
    for (const lane of ["A", "B", "C"]) {
      expect(screen.getByRole("button", { name: `Generating prompt ${lane}…` })).toBeDisabled();
    }

    await act(async () => resolveBenchmark(benchmarkResponseForPrompts(systemPrompts)));

    const resultA = screen.getByLabelText("Result A");
    const resultB = screen.getByLabelText("Result B");
    const resultC = screen.getByLabelText("Result C");
    expect(await within(resultA).findByRole("heading", {
      name: "Drivers reconsider the daily cost of congestion",
    })).toBeInTheDocument();
    expect(within(resultB).getByRole("heading", {
      name: "Employers weigh parking costs against cleaner air",
    })).toBeInTheDocument();
    expect(within(resultC).getByRole("heading", {
      name: "Bus passengers compare reliability with road delays",
    })).toBeInTheDocument();
    expect(within(resultA).getByText("Support the levy")).toBeInTheDocument();
    expect(screen.getByText("3 of 3 comparisons generated")).toBeInTheDocument();
    expect(screen.queryByText(/Generating comparison [ABC]…/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate all three" })).toBeEnabled();
    for (const editor of editors) expect(editor).toBeEnabled();
    for (const lane of ["A", "B", "C"]) {
      expect(screen.getByRole("button", { name: `Generate prompt ${lane}` })).toBeEnabled();
    }
  });

  it("isolates mixed generate-all failures and preserves results after request errors", async () => {
    const user = userEvent.setup();
    const systemPrompts = ["Prompt A", "Prompt B", "Prompt C"];
    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce(
      benchmarkResponseForPrompts(systemPrompts),
    );
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);

    const editors = await screen.findAllByDisplayValue("Production system prompt");
    for (const [index, systemPrompt] of systemPrompts.entries()) {
      await user.clear(editors[index]);
      await user.type(editors[index], systemPrompt);
    }
    await user.click(screen.getByRole("button", { name: "Generate all three" }));
    expect(await within(screen.getByLabelText("Result B")).findByRole("heading", {
      name: "Employers weigh parking costs against cleaner air",
    })).toBeInTheDocument();

    vi.mocked(generateUnwrappedBenchmark).mockResolvedValueOnce({
      ...benchmarkResponseForPrompts(systemPrompts),
      variants: [
        benchmarkResponse("Prompt A", "Drivers see a stronger case after revision").variants[0],
        { ...failedBenchmarkResponse("Prompt B", "Prompt B returned an invalid draft.").variants[0], position: 2 },
        benchmarkResponse("Prompt C", "Passengers see a stronger case after revision").variants[0],
      ],
    });
    await user.click(screen.getByRole("button", { name: "Generate all three" }));

    expect(await within(screen.getByLabelText("Result A")).findByRole("heading", {
      name: "Drivers see a stronger case after revision",
    })).toBeInTheDocument();
    const resultB = screen.getByLabelText("Result B");
    expect(within(resultB).getByRole("heading", {
      name: "Employers weigh parking costs against cleaner air",
    })).toBeInTheDocument();
    expect(within(resultB).getByRole("alert")).toHaveTextContent(
      "Prompt B returned an invalid draft.",
    );
    expect(within(screen.getByLabelText("Result C")).getByRole("heading", {
      name: "Passengers see a stronger case after revision",
    })).toBeInTheDocument();
    expect(screen.getByText("3 of 3 comparisons generated")).toBeInTheDocument();

    vi.mocked(generateUnwrappedBenchmark).mockRejectedValueOnce(
      new Error("The combined benchmark request timed out."),
    );
    await user.click(screen.getByRole("button", { name: "Generate all three" }));

    for (const lane of ["A", "B", "C"]) {
      const result = screen.getByLabelText(`Result ${lane}`);
      expect(within(result).getByRole("alert")).toHaveTextContent(
        "The combined benchmark request timed out.",
      );
      expect(within(result).getByRole("heading")).toBeInTheDocument();
    }
    expect(screen.getByRole("button", { name: "Generate all three" })).toBeEnabled();
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
    expect(screen.getByRole("button", { name: "Generate all three" })).toBeDisabled();
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

    vi.mocked(getUnwrappedBenchmarkPrompt).mockResolvedValue({
      ...benchmarkContext,
      systemPrompt: "Production prompt",
    });
    vi.mocked(generateUnwrappedBenchmark).mockRejectedValue(
      new Error("The provider timed out."),
    );
    render(<UnwrappedBenchmarkPage post={post} onBack={vi.fn()} />);
    const editors = await screen.findAllByDisplayValue("Production prompt");
    await user.clear(editors[1]);
    await user.type(editors[1], "   ");
    expect(screen.getByRole("button", { name: "Generate prompt B" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Generate prompt A" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Generate all three" })).toBeDisabled();
    expect(generateUnwrappedBenchmark).not.toHaveBeenCalled();

    await user.clear(editors[1]);
    await user.type(editors[1], "Valid replacement prompt");
    expect(screen.getByRole("button", { name: "Generate all three" })).toBeEnabled();
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
