import { renderHook, act } from "@testing-library/react-native";
import * as ImagePicker from "expo-image-picker";
import { useCreatePost, MAX_IMAGES } from "./use-create-post";
import { createPost } from "../services/PostService";
import { uploadMedia } from "../services/MediaUploadService";

jest.mock("expo-image-picker", () => ({
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
}));
jest.mock("../services/PostService", () => ({ createPost: jest.fn() }));
jest.mock("../services/MediaUploadService", () => ({ uploadMedia: jest.fn() }));

const mockCreate = createPost as jest.Mock;
const mockUpload = uploadMedia as jest.Mock;
const mockRequestPermission = ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock;
const mockLaunch = ImagePicker.launchImageLibraryAsync as jest.Mock;

const imageAsset = (uri: string) => ({ uri, type: "image", mimeType: "image/png" });

const validFields = {
  title: "Headline",
  summary: "A summary",
  supportQuestion: "Do you agree?",
};

beforeEach(() => {
  jest.clearAllMocks();
  mockRequestPermission.mockResolvedValue({ granted: true });
});

describe("useCreatePost validation", () => {
  it("flags every required field that is blank and does not call the API", async () => {
    const { result } = renderHook(() => useCreatePost());

    let created;
    await act(async () => {
      created = await result.current.submit({ title: "  ", summary: "", supportQuestion: "  " });
    });

    expect(created).toBeNull();
    expect(result.current.fieldErrors).toEqual({
      title: "Add a headline.",
      summary: "Add a summary.",
      supportQuestion: "Add a support question.",
    });
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it("flags only the blank field when others are filled", async () => {
    const { result } = renderHook(() => useCreatePost());

    await act(async () => {
      await result.current.submit({ ...validFields, supportQuestion: "" });
    });

    expect(result.current.fieldErrors).toEqual({ supportQuestion: "Add a support question." });
    expect(mockCreate).not.toHaveBeenCalled();
  });
});

describe("useCreatePost submit", () => {
  it("creates the post with trimmed fields and no media when nothing is picked", async () => {
    const post = { id: 1 };
    mockCreate.mockResolvedValue(post);
    const { result } = renderHook(() => useCreatePost());

    let created;
    await act(async () => {
      created = await result.current.submit({
        title: "  Headline  ",
        summary: "  A summary ",
        supportQuestion: " Do you agree? ",
      });
    });

    expect(mockUpload).not.toHaveBeenCalled();
    expect(mockCreate).toHaveBeenCalledWith({
      title: "Headline",
      summary: "A summary",
      supportQuestion: "Do you agree?",
      media: [],
    });
    expect(created).toBe(post);
    expect(result.current.error).toBeNull();
  });

  it("surfaces an error and returns null when create fails", async () => {
    mockCreate.mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() => useCreatePost());

    let created;
    await act(async () => {
      created = await result.current.submit(validFields);
    });

    expect(created).toBeNull();
    expect(result.current.error).toBe("network down");
    expect(result.current.submitting).toBe(false);
  });

  it("uploads every picked image and attaches its key (no poster) in order", async () => {
    mockLaunch.mockResolvedValue({
      canceled: false,
      assets: [imageAsset("file:///a.png"), imageAsset("file:///b.png")],
    });
    mockUpload
      .mockResolvedValueOnce({ mediaType: "IMAGE", s3Key: "posts/a.png", contentType: "image/png" })
      .mockResolvedValueOnce({ mediaType: "IMAGE", s3Key: "posts/b.png", contentType: "image/png" });
    mockCreate.mockResolvedValue({ id: 9 });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });
    expect(result.current.picked).toEqual([
      { uri: "file:///a.png", mediaType: "IMAGE", contentType: "image/png" },
      { uri: "file:///b.png", mediaType: "IMAGE", contentType: "image/png" },
    ]);

    await act(async () => {
      await result.current.submit(validFields);
    });

    expect(mockUpload).toHaveBeenCalledTimes(2);
    expect(mockUpload).toHaveBeenNthCalledWith(1, result.current.picked[0], expect.any(Function));
    expect(mockCreate).toHaveBeenCalledWith({
      title: "Headline",
      summary: "A summary",
      supportQuestion: "Do you agree?",
      media: [
        { mediaType: "IMAGE", s3Key: "posts/a.png", contentType: "image/png", posterS3Key: null },
        { mediaType: "IMAGE", s3Key: "posts/b.png", contentType: "image/png", posterS3Key: null },
      ],
    });
  });
});

describe("useCreatePost media selection", () => {
  it("caps images at MAX_IMAGES even if more are returned", async () => {
    const six = Array.from({ length: 6 }, (_, i) => imageAsset(`file:///${i}.png`));
    mockLaunch.mockResolvedValue({ canceled: false, assets: six });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });

    expect(result.current.picked).toHaveLength(MAX_IMAGES);
  });

  it("refuses to add beyond MAX_IMAGES: errors and never re-opens the picker", async () => {
    const five = Array.from({ length: MAX_IMAGES }, (_, i) => imageAsset(`file:///${i}.png`));
    mockLaunch.mockResolvedValueOnce({ canceled: false, assets: five });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });
    expect(result.current.picked).toHaveLength(MAX_IMAGES);

    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });
    // The second pick short-circuits on the full selection — the picker is not launched again.
    expect(mockLaunch).toHaveBeenCalledTimes(1);
    expect(result.current.picked).toHaveLength(MAX_IMAGES);
    expect(result.current.error).toBe("You can attach up to 5 images.");
  });

  it("picking a video replaces any images (a post is images OR a video)", async () => {
    mockLaunch.mockResolvedValueOnce({
      canceled: false,
      assets: [imageAsset("file:///a.png"), imageAsset("file:///b.png")],
    });
    mockLaunch.mockResolvedValueOnce({
      canceled: false,
      assets: [{ uri: "file:///clip.mov", type: "video" }],
    });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });
    await act(async () => {
      await result.current.pickMedia("VIDEO");
    });

    expect(result.current.picked).toEqual([
      { uri: "file:///clip.mov", mediaType: "VIDEO", contentType: "video/mp4" },
    ]);
  });

  it("removes a single picked image by index", async () => {
    mockLaunch.mockResolvedValue({
      canceled: false,
      assets: [imageAsset("file:///a.png"), imageAsset("file:///b.png")],
    });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });
    act(() => {
      result.current.removeMedia(0);
    });

    expect(result.current.picked).toEqual([
      { uri: "file:///b.png", mediaType: "IMAGE", contentType: "image/png" },
    ]);
  });

  it("does not stash media or error when the user cancels the picker", async () => {
    mockLaunch.mockResolvedValue({ canceled: true, assets: [] });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });

    expect(result.current.picked).toEqual([]);
    expect(result.current.error).toBeNull();
  });

  it("sets a permission error and picks nothing when library access is denied", async () => {
    mockRequestPermission.mockResolvedValue({ granted: false });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });

    expect(mockLaunch).not.toHaveBeenCalled();
    expect(result.current.picked).toEqual([]);
    expect(result.current.error).toBe("We need photo library access to attach media.");
  });

  it("falls back to a default content type when a picked asset has no mimeType", async () => {
    mockLaunch.mockResolvedValue({
      canceled: false,
      assets: [{ uri: "file:///photo.heic", type: "image" }],
    });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia("IMAGE");
    });

    expect(result.current.picked).toEqual([
      { uri: "file:///photo.heic", mediaType: "IMAGE", contentType: "image/jpeg" },
    ]);
  });
});
