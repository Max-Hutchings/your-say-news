import { act, renderHook, waitFor } from "@testing-library/react-native";
import { useUnwrapped } from "./use-unwrapped";
import { getUnwrapped, submitFollowUp } from "../services/UnwrappedService";

jest.mock("../services/UnwrappedService");
const mockGet = getUnwrapped as jest.Mock;
const mockSubmit = submitFollowUp as jest.Mock;

const ready = {
  state: "READY",
  notice: "Observed story",
  originalOptionId: 71,
  existingFollowUpOptionId: null,
  story: { storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f" },
};

beforeEach(() => {
  jest.clearAllMocks();
  mockGet.mockResolvedValue(ready);
});

test("loads the requested post and exposes its exact response", async () => {
  const { result } = renderHook(() => useUnwrapped(7));
  expect(result.current.loading).toBe(true);

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(mockGet).toHaveBeenCalledWith(7);
  expect(result.current.data).toEqual(ready);
  expect(result.current.error).toBe(false);
});

test("successful follow-up updates the recorded option while a failure keeps story data for retry", async () => {
  mockSubmit.mockResolvedValueOnce({ optionId: 72 });
  const { result } = renderHook(() => useUnwrapped(7));
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => {
    await expect(result.current.followUp(ready.story.storyId, 72)).resolves.toBe(true);
  });
  expect(result.current.data).toEqual({ ...ready, existingFollowUpOptionId: 72 });

  mockSubmit.mockRejectedValueOnce(new Error("offline"));
  await act(async () => {
    await expect(result.current.followUp(ready.story.storyId, 71)).resolves.toBe(false);
  });
  expect(result.current.error).toBe(true);
  expect(result.current.data?.story).toEqual(ready.story);
  expect(result.current.submitting).toBe(false);
});

test("coalesces rapid duplicate follow-up taps into one network write", async () => {
  let resolve!: (value: { optionId: number }) => void;
  mockSubmit.mockReturnValue(new Promise((done) => { resolve = done; }));
  const { result } = renderHook(() => useUnwrapped(7));
  await waitFor(() => expect(result.current.loading).toBe(false));

  let first!: Promise<boolean>;
  let second!: Promise<boolean>;
  act(() => {
    first = result.current.followUp(ready.story.storyId, 72);
    second = result.current.followUp(ready.story.storyId, 72);
  });
  await expect(second).resolves.toBe(false);
  expect(mockSubmit).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolve({ optionId: 72 });
    await expect(first).resolves.toBe(true);
  });
});

test("an old post response cannot overwrite a newly requested post", async () => {
  let resolveOld!: (value: typeof ready) => void;
  const newer = { ...ready, notice: "Story for post 8", originalOptionId: 81 };
  mockGet.mockImplementation((postId: number) => postId === 7
    ? new Promise((done) => { resolveOld = done; })
    : Promise.resolve(newer));
  const { result, rerender } = renderHook<
    ReturnType<typeof useUnwrapped>,
    { postId: number }
  >(
    ({ postId }) => useUnwrapped(postId),
    { initialProps: { postId: 7 } }
  );

  rerender({ postId: 8 });
  await waitFor(() => expect(result.current.data).toEqual(newer));

  await act(async () => {
    resolveOld(ready);
    await Promise.resolve();
  });
  expect(result.current.data).toEqual(newer);
  expect(result.current.loading).toBe(false);
});
