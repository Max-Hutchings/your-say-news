import { renderHook, act, waitFor } from "@testing-library/react-native";
import { useVote } from "./use-vote";
import { castVote, getMyVote } from "../services/VoteService";

jest.mock("../services/VoteService");

const mockCast = castVote as jest.Mock;
const mockGetMine = getMyVote as jest.Mock;

/** A rejection shaped like an axios error so the hook's classifier recognises it. */
function axiosError(status?: number) {
  return { isAxiosError: true, response: status == null ? undefined : { status } };
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe("useVote", () => {
  it("locks to the caller's existing vote fetched on mount", async () => {
    mockGetMine.mockResolvedValue({ id: 1, postId: 7, voteFor: true });

    const { result } = renderHook(() => useVote(7));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockGetMine).toHaveBeenCalledWith(7);
    expect(result.current.myVote).toBe(true);
  });

  it("stays unvoted when the caller has not voted (204 → null)", async () => {
    mockGetMine.mockResolvedValue(null);

    const { result } = renderHook(() => useVote(7));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.myVote).toBeNull();
  });

  it("casts a vote and locks to the chosen stance", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 2, postId: 7, voteFor: true });

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.vote(true);
    });

    expect(mockCast).toHaveBeenCalledWith(7, true);
    expect(result.current.myVote).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it("ignores a second vote once locked (one vote per post)", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 2, postId: 7, voteFor: true });

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.vote(true);
    });
    await act(async () => {
      await result.current.vote(false);
    });

    expect(mockCast).toHaveBeenCalledTimes(1);
    expect(result.current.myVote).toBe(true);
  });

  it("blocks a second rapid vote while the first request is still in flight", async () => {
    mockGetMine.mockResolvedValue(null);
    let resolveCast: ((value: { id: number; postId: number; voteFor: boolean }) => void) | undefined;
    mockCast.mockReturnValue(
      new Promise((resolve) => {
        resolveCast = resolve;
      })
    );

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      void result.current.vote(true);
      void result.current.vote(false);
    });

    expect(mockCast).toHaveBeenCalledTimes(1);
    expect(mockCast).toHaveBeenCalledWith(7, true);

    await act(async () => {
      resolveCast?.({ id: 2, postId: 7, voteFor: true });
    });
    expect(result.current.myVote).toBe(true);
  });

  it("treats a 409 duplicate as already-voted: reconciles to the stored stance, no error", async () => {
    mockGetMine
      .mockResolvedValueOnce(null) // mount: appears unvoted
      .mockResolvedValueOnce({ id: 5, postId: 7, voteFor: false }); // reconcile after 409
    mockCast.mockRejectedValue(axiosError(409));

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.vote(true);
    });

    expect(result.current.myVote).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("surfaces an auth error and leaves the post votable to retry", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockRejectedValue(axiosError(401));

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.vote(true);
    });

    expect(result.current.error).toBe("auth");
    expect(result.current.myVote).toBeNull();
  });

  it("surfaces a network error when the request never reached the server", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockRejectedValue(axiosError()); // no response

    const { result } = renderHook(() => useVote(7));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.vote(false);
    });

    expect(result.current.error).toBe("network");
    expect(result.current.myVote).toBeNull();
  });
});
