import axios from "axios";
import { uploadMedia } from "./MediaUploadService";
import { YsnHttpClient } from "@/features/auth";

jest.mock("axios", () => ({ __esModule: true, default: { put: jest.fn() } }));
jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        POST_SERVICE_HOST: "http://posts.local:",
        POST_SERVICE_PORT: "8082",
      },
    },
  },
}));

const mockPresignPost = jest.fn();
jest.mock("@/features/auth", () => ({
  YsnHttpClient: { getSecure: () => ({ post: (...args: unknown[]) => mockPresignPost(...args) }) },
}));

const mockPut = (axios as unknown as { put: jest.Mock }).put;

const localImage = {
  uri: "file:///tmp/pic.jpg",
  mediaType: "IMAGE" as const,
  contentType: "image/jpeg",
};

beforeEach(() => {
  jest.clearAllMocks();
  mockPresignPost.mockResolvedValue({
    data: { s3Key: "posts/pic.jpg", uploadUrl: "https://s3.local/put", expiresInSeconds: 900 },
  });
  // global fetch -> blob, used to read the local file before PUT.
  global.fetch = jest.fn(async () => ({ blob: async () => "BYTES" })) as unknown as typeof fetch;
});

describe("uploadMedia", () => {
  it("presigns, PUTs the bytes to S3 with the content type, and returns the key", async () => {
    mockPut.mockResolvedValue({ status: 200 });

    const result = await uploadMedia(localImage);

    expect(mockPresignPost).toHaveBeenCalledWith(
      "http://posts.local:8082/posts/media/presign",
      {
        mediaType: "IMAGE",
        contentType: "image/jpeg",
      }
    );
    expect(global.fetch).toHaveBeenCalledWith("file:///tmp/pic.jpg");
    expect(mockPut).toHaveBeenCalledWith(
      "https://s3.local/put",
      "BYTES",
      expect.objectContaining({ headers: { "Content-Type": "image/jpeg" } })
    );
    expect(result).toEqual({
      mediaType: "IMAGE",
      s3Key: "posts/pic.jpg",
      contentType: "image/jpeg",
    });
  });

  it("reports upload progress as a 0..1 fraction and finishes at 1", async () => {
    const progress: number[] = [];
    mockPut.mockImplementation(async (_url: string, _body: unknown, config: any) => {
      config.onUploadProgress({ loaded: 50, total: 200 });
      config.onUploadProgress({ loaded: 200, total: 200 });
      return { status: 200 };
    });

    await uploadMedia(localImage, (f) => progress.push(f));

    // 0.25, 1 (from PUT events) then a final 1 emitted on completion.
    expect(progress).toEqual([0.25, 1, 1]);
  });

  it("clamps progress to 1 even if S3 reports more bytes loaded than total", async () => {
    const progress: number[] = [];
    mockPut.mockImplementation(async (_url: string, _body: unknown, config: any) => {
      config.onUploadProgress({ loaded: 300, total: 200 });
      return { status: 200 };
    });

    await uploadMedia(localImage, (f) => progress.push(f));

    // 300/200 = 1.5 clamped to 1, then the completion emit.
    expect(progress).toEqual([1, 1]);
  });

  it("reports 0 progress when S3 does not include a total byte count", async () => {
    const progress: number[] = [];
    mockPut.mockImplementation(async (_url: string, _body: unknown, config: any) => {
      config.onUploadProgress({ loaded: 50 });
      return { status: 200 };
    });

    await uploadMedia(localImage, (f) => progress.push(f));

    expect(progress).toEqual([0, 1]);
  });

  it("propagates a failed S3 upload", async () => {
    mockPut.mockRejectedValue(new Error("S3 rejected the upload"));

    await expect(uploadMedia(localImage)).rejects.toThrow("S3 rejected the upload");
  });

  it("sends the S3 PUT with a bare client carrying no Authorization header", async () => {
    mockPut.mockResolvedValue({ status: 200 });

    await uploadMedia(localImage);

    // Security guard: only the presign call is bearer-authenticated (via YsnHttpClient). The S3 PUT
    // goes through plain axios with no bearer — a token would break the presigned signature and
    // needlessly leak our credentials to storage. Pin that the PUT carries Content-Type and nothing else.
    expect(mockPut).toHaveBeenCalledTimes(1);
    const config = mockPut.mock.calls[0][2] as { headers: Record<string, string> };
    expect(config.headers).toEqual({ "Content-Type": "image/jpeg" });
    expect(config.headers).not.toHaveProperty("Authorization");
  });
});
