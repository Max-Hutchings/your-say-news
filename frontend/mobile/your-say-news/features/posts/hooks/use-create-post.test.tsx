import { renderHook, act } from "@testing-library/react-native";
import * as ImagePicker from "expo-image-picker";
import { useCreatePost } from "./use-create-post";
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

const pickedImageAsset = {
  uri: "file:///tmp/photo.jpg",
  type: "image",
  mimeType: "image/png",
};

const validFields = {
  title: "Headline",
  summary: "A summary",
  supportQuestion: "Do you agree?",
};

beforeEach(() => {
  jest.clearAllMocks();
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

    expect(result.current.fieldErrors).toEqual({
      supportQuestion: "Add a support question.",
    });
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

  it("uploads a picked asset and attaches its key (no poster for an image) to the post", async () => {
    mockRequestPermission.mockResolvedValue({ granted: true });
    mockLaunch.mockResolvedValue({ canceled: false, assets: [pickedImageAsset] });
    mockUpload.mockResolvedValue({
      mediaType: "IMAGE",
      s3Key: "posts/uploaded.png",
      contentType: "image/png",
    });
    mockCreate.mockResolvedValue({ id: 9 });

    const { result } = renderHook(() => useCreatePost());

    await act(async () => {
      await result.current.pickMedia();
    });
    // The picked asset is stashed locally, derived from the asset's mime/type.
    expect(result.current.picked).toEqual({
      uri: "file:///tmp/photo.jpg",
      mediaType: "IMAGE",
      contentType: "image/png",
    });

    await act(async () => {
      await result.current.submit(validFields);
    });

    expect(mockUpload).toHaveBeenCalledWith(result.current.picked, expect.any(Function));
    expect(mockCreate).toHaveBeenCalledWith({
      title: "Headline",
      summary: "A summary",
      supportQuestion: "Do you agree?",
      media: [
        {
          mediaType: "IMAGE",
          s3Key: "posts/uploaded.png",
          contentType: "image/png",
          posterS3Key: null,
        },
      ],
    });
  });

  it("does not stash media or error when the user cancels the picker", async () => {
    mockRequestPermission.mockResolvedValue({ granted: true });
    mockLaunch.mockResolvedValue({ canceled: true, assets: [] });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia();
    });

    expect(result.current.picked).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("sets a permission error and picks nothing when library access is denied", async () => {
    mockRequestPermission.mockResolvedValue({ granted: false });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia();
    });

    expect(mockLaunch).not.toHaveBeenCalled();
    expect(result.current.picked).toBeNull();
    expect(result.current.error).toBe("We need photo library access to attach media.");
  });
});

describe("useCreatePost media-type mapping", () => {
  it("derives VIDEO media with a default video/mp4 content type from a picked video", async () => {
    mockRequestPermission.mockResolvedValue({ granted: true });
    // A video asset with no mimeType exercises both the VIDEO branch and the mp4 fallback.
    mockLaunch.mockResolvedValue({
      canceled: false,
      assets: [{ uri: "file:///tmp/clip.mov", type: "video" }],
    });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia();
    });

    expect(result.current.picked).toEqual({
      uri: "file:///tmp/clip.mov",
      mediaType: "VIDEO",
      contentType: "video/mp4",
    });
  });

  it("falls back to image/jpeg when a picked image has no mimeType", async () => {
    mockRequestPermission.mockResolvedValue({ granted: true });
    mockLaunch.mockResolvedValue({
      canceled: false,
      assets: [{ uri: "file:///tmp/photo.heic", type: "image" }],
    });

    const { result } = renderHook(() => useCreatePost());
    await act(async () => {
      await result.current.pickMedia();
    });

    expect(result.current.picked).toEqual({
      uri: "file:///tmp/photo.heic",
      mediaType: "IMAGE",
      contentType: "image/jpeg",
    });
  });
});
