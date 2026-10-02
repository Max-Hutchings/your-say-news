import { renderHook, waitFor } from "@testing-library/react-native";
import { resetUnwrappedFeatures, useUnwrappedFeatures } from "./use-unwrapped-features";
import { getUnwrappedFeatures } from "../services/UnwrappedService";

jest.mock("../services/UnwrappedService");
const mockGetFeatures = getUnwrappedFeatures as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  resetUnwrappedFeatures();
});

test("is unknown until the backend answers, then exposes its flags", async () => {
  mockGetFeatures.mockResolvedValue({ enabled: true, unwrapButton: false });
  const { result } = renderHook(() => useUnwrappedFeatures());
  expect(result.current).toBeNull();

  await waitFor(() => expect(result.current).toEqual({ enabled: true, unwrapButton: false }));
});

test("fetches the flags once for every card in the feed", async () => {
  mockGetFeatures.mockResolvedValue({ enabled: true, unwrapButton: true });
  const first = renderHook(() => useUnwrappedFeatures());
  const second = renderHook(() => useUnwrappedFeatures());

  await waitFor(() => expect(second.result.current).toEqual({ enabled: true, unwrapButton: true }));
  expect(first.result.current).toEqual({ enabled: true, unwrapButton: true });
  expect(mockGetFeatures).toHaveBeenCalledTimes(1);
});

test("treats Unwrapped as off when the flags cannot be loaded, and retries on the next mount", async () => {
  mockGetFeatures.mockRejectedValueOnce(new Error("offline"));
  const failed = renderHook(() => useUnwrappedFeatures());
  await waitFor(() => expect(failed.result.current).toEqual({ enabled: false, unwrapButton: false }));

  mockGetFeatures.mockResolvedValue({ enabled: true, unwrapButton: true });
  const retried = renderHook(() => useUnwrappedFeatures());
  await waitFor(() => expect(retried.result.current).toEqual({ enabled: true, unwrapButton: true }));
  expect(mockGetFeatures).toHaveBeenCalledTimes(2);
});
