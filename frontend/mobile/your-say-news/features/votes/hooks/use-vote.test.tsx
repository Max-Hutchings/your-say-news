import { act, renderHook, waitFor } from "@testing-library/react-native";
import { useVote } from "./use-vote";
import { castVote, getMyVote } from "../services/VoteService";

jest.mock("../services/VoteService");
const mockCast = castVote as jest.Mock;
const mockGetMine = getMyVote as jest.Mock;
const axiosError = (status?: number) => ({ isAxiosError: true, response: status == null ? undefined : { status } });

beforeEach(() => jest.clearAllMocks());

describe("useVote", () => {
  it("locks to the exact existing option id", async () => {
    mockGetMine.mockResolvedValue({ id: 1, postId: 7, optionId: 72 });
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.myVote).toBe(72);
    expect(result.current.locked).toBe(true);
  });

  it("casts one selected option and ignores a second selection after locking", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 2, postId: 7, optionId: 73 });
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => { await result.current.vote(73); });
    await act(async () => { await result.current.vote(74); });
    expect(mockCast).toHaveBeenCalledTimes(1);
    expect(mockCast).toHaveBeenCalledWith(7, 73);
    expect(result.current.myVote).toBe(73);
    expect(result.current.locked).toBe(true);
  });

  it("blocks a rapid second request while the first is pending", async () => {
    mockGetMine.mockResolvedValue(null);
    let resolveCast: ((vote: { id: number; postId: number; optionId: number }) => void) | undefined;
    mockCast.mockReturnValue(new Promise((resolve) => { resolveCast = resolve; }));
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => { void result.current.vote(71); void result.current.vote(72); });
    expect(mockCast).toHaveBeenCalledTimes(1);
    await act(async () => resolveCast?.({ id: 2, postId: 7, optionId: 71 }));
    expect(result.current.myVote).toBe(71);
  });

  it("reconciles a duplicate to the stored option id", async () => {
    mockGetMine.mockResolvedValueOnce(null).mockResolvedValueOnce({ id: 5, postId: 7, optionId: 72 });
    mockCast.mockRejectedValue(axiosError(409));
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => { await result.current.vote(71); });
    expect(result.current.myVote).toBe(72);
    expect(result.current.locked).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it("uses a neutral locked state when duplicate reconciliation cannot retrieve the stored choice", async () => {
    mockGetMine.mockResolvedValueOnce(null).mockRejectedValueOnce(new Error("offline"));
    mockCast.mockRejectedValue(axiosError(409));
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => { await result.current.vote(71); });
    expect(result.current.myVote).toBeNull();
    expect(result.current.locked).toBe(true);
  });

  it("surfaces auth and network failures without locking", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockRejectedValueOnce(axiosError(401)).mockRejectedValueOnce(axiosError());
    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => { await result.current.vote(71); });
    expect(result.current.error).toBe("auth");
    expect(result.current.locked).toBe(false);
    await act(async () => { await result.current.vote(71); });
    expect(result.current.error).toBe("network");
    expect(result.current.locked).toBe(false);
  });
});
