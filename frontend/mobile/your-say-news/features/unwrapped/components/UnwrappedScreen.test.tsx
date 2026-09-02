import React from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react-native";
import { Linking } from "react-native";
import { ThemeProvider } from "@/constants/theme";
import { UnwrappedScreen } from "./UnwrappedScreen";
import { useUnwrapped } from "../hooks/use-unwrapped";
import type {
  UnwrappedArgumentPage,
  UnwrappedResponse,
  UnwrappedSource,
} from "../types";

const mockBack = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack }),
}));
jest.mock("../hooks/use-unwrapped");
jest.mock("@/features/votes", () => ({
  SentimentResults: ({ postId }: { postId: number }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Text } = require("react-native");
    return <Text>Live results for {postId}</Text>;
  },
}));

const mockUseUnwrapped = useUnwrapped as jest.Mock;
const followUp = jest.fn();

const options = [
  { id: 71, label: "Reduce public spending", ordinal: 0, semanticKey: null },
  { id: 72, label: "Keep public services funded", ordinal: 1, semanticKey: null },
];
const source: UnwrappedSource = {
  id: "source-1",
  url: "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes",
  publisher: "Office for National Statistics",
  title: "Public sector finances",
  classification: "OFFICIAL",
};
const servicesSource: UnwrappedSource = {
  id: "source-2",
  url: "https://www.instituteforgovernment.org.uk/publication/performance-tracker-2025",
  publisher: "Institute for Government",
  title: "Public services performance tracker",
  classification: "OTHER",
};
const taxSource: UnwrappedSource = {
  id: "source-3",
  url: "https://ifs.org.uk/publications/tax-outlook",
  publisher: "Institute for Fiscal Studies",
  title: "Tax outlook",
  classification: "ACADEMIC",
};
const page = (
  optionId: number,
  headline: string,
  sources: UnwrappedSource[] = [source]
): UnwrappedArgumentPage => ({
  optionId,
  headline,
  selectedCohortIds: optionId === 71 ? ["ageRange=AGE_25_34"] : ["occupation=PUBLIC_SECTOR"],
  paragraphs: [{
    text: "Younger adults are likely to favour lower spending because current deductions leave less room in already stretched monthly budgets, making a visible reduction feel more urgent than benefits promised later.",
    sourceIds: sources.map((item) => item.id),
  }, {
    text: "Official figures show how the financial trade-off has changed over time. For these voters, immediate take-home pay can feel more valuable than distant benefits that are harder to see.",
    sourceIds: sources.map((item) => item.id),
  }],
  caveat: "This association describes only people who voted on this post and does not represent any broader population.",
  sources,
});

function response(argumentPages: UnwrappedArgumentPage[] = [
  page(71, "The case for taking less in tax", [source, taxSource]),
  page(72, "The case for protecting shared services", [servicesSource]),
]): UnwrappedResponse {
  return {
    state: "READY",
    notice: "This analysis describes people who voted on this post; it is not a population survey.",
    originalOptionId: 71,
    existingFollowUpOptionId: null,
    story: {
      schemaVersion: "unwrapped-story-v2",
      storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f",
      postId: 7,
      milestone: 100,
      canonicalVoteCount: 126,
      aggregateVersion: "sha256:fixture",
      generatedAt: "2026-07-25T12:00:00Z",
      model: "configured-model",
      argumentPages,
      reconsiderationQuestion: "Has seeing the context for every option changed your view?",
      reconsiderationOptions: options,
    },
  };
}

function renderScreen() {
  return render(<ThemeProvider><UnwrappedScreen postId={7} /></ThemeProvider>);
}

beforeEach(() => {
  jest.clearAllMocks();
  followUp.mockResolvedValue(true);
  mockUseUnwrapped.mockReturnValue({
    data: response(),
    loading: false,
    submitting: false,
    error: false,
    refresh: jest.fn(),
    followUp,
  });
});

test("renders article Markdown without changing its citations", () => {
  const markdownPage = page(71, "Why younger adults favour lower deductions", [source]);
  markdownPage.paragraphs = [{
    text: "**Younger adults**\n\nThis group may prefer lower deductions because monthly costs are immediate.\n\n- Lower monthly costs\n- More **essential spending**",
    sourceIds: [source.id],
  }];
  mockUseUnwrapped.mockReturnValue({
    data: response([markdownPage]),
    loading: false,
    submitting: false,
    error: false,
    refresh: jest.fn(),
    followUp,
  });

  renderScreen();

  expect(screen.getByText("Younger adults")).toHaveStyle({ fontWeight: "700" });
  expect(screen.getByText("Lower monthly costs")).toBeOnTheScreen();
  expect(screen.getByText("essential spending")).toHaveStyle({ fontWeight: "700" });
  expect(screen.getByText("[1]")).toBeOnTheScreen();
  expect(screen.getByText("Public sector finances")).toBeOnTheScreen();
});

test("renders the binary three-page sequence, requires a second answer, then opens live results", async () => {
  renderScreen();

  expect(screen.getByText("POST UNWRAPPED · 1 OF 3")).toBeOnTheScreen();
  expect(screen.getByText("Reduce public spending")).toBeOnTheScreen();
  expect(screen.getByText("The case for taking less in tax")).toBeOnTheScreen();
  expect(screen.queryByText(
    "This analysis describes people who voted on this post; it is not a population survey."
  )).toBeNull();
  expect(screen.getByText(
    "Younger adults are likely to favour lower spending because current deductions leave less room in already stretched monthly budgets, making a visible reduction feel more urgent than benefits promised later."
  )).toBeOnTheScreen();
  expect(screen.getAllByText("[1] [2]")).toHaveLength(2);
  expect(screen.getByText(
    "Official figures show how the financial trade-off has changed over time. For these voters, immediate take-home pay can feel more valuable than distant benefits that are harder to see."
  )).toBeOnTheScreen();
  expect(screen.queryByText(
    "This association describes only people who voted on this post and does not represent any broader population."
  )).toBeNull();
  expect(screen.queryByText("OBSERVED HERE")).toBeNull();
  expect(screen.queryByText("WIDER CONTEXT")).toBeNull();
  expect(screen.queryByText("Public services performance tracker")).toBeNull();
  const sourceLinks = screen.getAllByRole("link");
  expect(sourceLinks).toHaveLength(2);
  expect(within(sourceLinks[0]).getByText("01")).toBeOnTheScreen();
  expect(within(sourceLinks[0]).getByText("Public sector finances")).toBeOnTheScreen();
  expect(within(sourceLinks[0]).getByText(
    "Office for National Statistics · OFFICIAL"
  )).toBeOnTheScreen();
  expect(within(sourceLinks[1]).getByText("02")).toBeOnTheScreen();
  expect(within(sourceLinks[1]).getByText("Tax outlook")).toBeOnTheScreen();
  expect(within(sourceLinks[1]).getByText(
    "Institute for Fiscal Studies · ACADEMIC"
  )).toBeOnTheScreen();
  const link = jest.spyOn(Linking, "openURL").mockResolvedValue(undefined);
  fireEvent.press(sourceLinks[0]);
  fireEvent.press(sourceLinks[1]);
  expect(link).toHaveBeenNthCalledWith(1, source.url);
  expect(link).toHaveBeenNthCalledWith(2, taxSource.url);

  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
  expect(screen.getByText("POST UNWRAPPED · 2 OF 3")).toBeOnTheScreen();
  expect(screen.getByText("The case for protecting shared services")).toBeOnTheScreen();
  expect(screen.getByText("Public services performance tracker")).toBeOnTheScreen();
  expect(screen.getAllByText("[1]")).toHaveLength(2);
  expect(screen.queryByText("[0]")).toBeNull();
  expect(screen.queryByText("[2]")).toBeNull();
  expect(screen.queryByText("[3]")).toBeNull();
  expect(screen.queryByText("Public sector finances")).toBeNull();
  expect(screen.queryByText("Tax outlook")).toBeNull();
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));

  expect(screen.getByText("POST UNWRAPPED · 3 OF 3")).toBeOnTheScreen();
  expect(screen.getByRole("button", { name: "See live results" }).props.accessibilityState.disabled)
    .toBe(true);
  fireEvent.press(screen.getByRole("radio", { name: "Keep public services funded" }));
  fireEvent.press(screen.getByRole("button", { name: "See live results" }));

  await waitFor(() => expect(followUp).toHaveBeenCalledWith(
    "4e11bdba-3ae0-4c76-963a-d5b3b2db597f", 72
  ));
  expect(await screen.findByText("Live results for 7")).toBeOnTheScreen();
});

test("reaches all five arguments in order and then the sixth reconsideration page", () => {
  const fiveOptions = Array.from({ length: 5 }, (_, index) => ({
    id: 80 + index,
    label: `Policy option ${index + 1}`,
    ordinal: index,
    semanticKey: null,
  }));
  const fivePages = fiveOptions.map((option) => page(option.id, `Case ${option.label}`));
  const data = response(fivePages);
  data.story!.reconsiderationOptions = fiveOptions;
  mockUseUnwrapped.mockReturnValue({
    data, loading: false, submitting: false, error: false, refresh: jest.fn(), followUp,
  });

  renderScreen();

  expect(screen.getByText("POST UNWRAPPED · 1 OF 6")).toBeOnTheScreen();
  expect(screen.getByText("Case Policy option 1")).toBeOnTheScreen();
  for (let index = 1; index < fivePages.length; index++) {
    fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
    expect(screen.getByText(`POST UNWRAPPED · ${index + 1} OF 6`)).toBeOnTheScreen();
    expect(screen.getByText(`Case Policy option ${index + 1}`)).toBeOnTheScreen();
  }
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
  expect(screen.getByText("POST UNWRAPPED · 6 OF 6")).toBeOnTheScreen();
  expect(screen.getByText("Has seeing the context for every option changed your view?"))
    .toBeOnTheScreen();
});

test("an existing follow-up is locked and can proceed without writing a second response", () => {
  const data = response();
  data.existingFollowUpOptionId = 72;
  mockUseUnwrapped.mockReturnValue({
    data, loading: false, submitting: false, error: false, refresh: jest.fn(), followUp,
  });
  renderScreen();
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));

  expect(screen.getByRole("radio", { name: "Keep public services funded" })
    .props.accessibilityState).toEqual({ checked: true, disabled: true });
  fireEvent.press(screen.getByRole("button", { name: "See live results" }));

  expect(followUp).not.toHaveBeenCalled();
  expect(screen.getByText("Live results for 7")).toBeOnTheScreen();
});

test("a failed follow-up keeps the voter on the reconsideration page", async () => {
  followUp.mockResolvedValue(false);
  mockUseUnwrapped.mockReturnValue({
    data: response(), loading: false, submitting: false, error: true,
    refresh: jest.fn(), followUp,
  });
  renderScreen();
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
  fireEvent.press(screen.getByRole("button", { name: "Next argument" }));
  fireEvent.press(screen.getByRole("radio", { name: "Keep public services funded" }));
  fireEvent.press(screen.getByRole("button", { name: "See live results" }));

  await waitFor(() => expect(followUp).toHaveBeenCalledTimes(1));
  expect(screen.queryByText("Live results for 7")).toBeNull();
  expect(screen.getByText("Has seeing the context for every option changed your view?"))
    .toBeOnTheScreen();
  expect(screen.getByRole("alert")).toHaveTextContent(
    "Your follow-up was not saved. Check your connection and try again."
  );
});

test("building state allows retry or continuing to factual results", () => {
  const refresh = jest.fn();
  mockUseUnwrapped.mockReturnValue({
    data: {
      state: "BUILDING",
      notice: "Pepper is building the context for every option.",
      originalOptionId: 71,
      existingFollowUpOptionId: null,
      story: null,
    },
    loading: false, submitting: false, error: false, refresh, followUp,
  });
  renderScreen();

  expect(screen.getByText("Pepper is building the context for every option.")).toBeOnTheScreen();
  fireEvent.press(screen.getByRole("button", { name: "Check again" }));
  expect(refresh).toHaveBeenCalledTimes(1);
  fireEvent.press(screen.getByRole("button", { name: "Continue to factual results" }));
  expect(screen.getByText("Live results for 7")).toBeOnTheScreen();
});
