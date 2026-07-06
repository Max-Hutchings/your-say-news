import { useCallback, useState } from "react";
import * as ImagePicker from "expo-image-picker";
import { createPost } from "../services/PostService";
import { uploadMedia, type LocalMedia } from "../services/MediaUploadService";
import type { CreatePostMedia, MediaType, Post } from "../types";

/**
 * Orchestrates the create-post flow: pick media → presign + upload (with
 * progress) → create the post. Keeps the route thin by owning the picked
 * assets, upload progress, loading and error state in one place.
 *
 * A post carries EITHER up to five images (shown as a swipeable carousel in the
 * feed) OR a single video (auto-played). The two are mutually exclusive: adding
 * images drops any picked video, and picking a video replaces everything.
 */

/** A post may carry at most this many images (mirrors the post-service ceiling). */
export const MAX_IMAGES = 5;

export interface CreatePostFields {
  title: string;
  summary: string;
  supportQuestion: string;
}

/** Trimmed-empty checks for the three required fields; keys with errors map to a message. */
export type CreatePostErrors = Partial<Record<keyof CreatePostFields, string>>;

function validate(fields: CreatePostFields): CreatePostErrors {
  const errors: CreatePostErrors = {};
  if (!fields.title.trim()) errors.title = "Add a headline.";
  if (!fields.summary.trim()) errors.summary = "Add a summary.";
  if (!fields.supportQuestion.trim()) errors.supportQuestion = "Add a support question.";
  return errors;
}

/** Map a picker asset's MIME-ish info to our contentType + mediaType + orientation. */
function toLocalMedia(asset: ImagePicker.ImagePickerAsset): LocalMedia {
  const mediaType: MediaType = asset.type === "video" ? "VIDEO" : "IMAGE";
  const contentType =
    asset.mimeType ?? (mediaType === "VIDEO" ? "video/mp4" : "image/jpeg");
  // Taller-than-wide assets render in the portrait layout; everything else (incl. square) is landscape.
  const orientation = asset.height > asset.width ? "PORTRAIT" : "LANDSCAPE";
  return { uri: asset.uri, mediaType, orientation, contentType };
}

export function useCreatePost() {
  const [picked, setPicked] = useState<LocalMedia[]>([]);
  const [progress, setProgress] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<CreatePostErrors>({});

  /**
   * Open the gallery and stash the chosen media locally (no upload yet).
   * `"IMAGE"` allows a multi-select up to the remaining slots and appends to
   * any images already picked; `"VIDEO"` takes a single clip and replaces all.
   */
  const pickMedia = useCallback(
    async (kind: MediaType = "IMAGE"): Promise<void> => {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        setError("We need photo library access to attach media.");
        return;
      }

      if (kind === "VIDEO") {
        const result = await ImagePicker.launchImageLibraryAsync({
          mediaTypes: ["videos"],
          quality: 0.8,
        });
        if (result.canceled || result.assets.length === 0) return;
        setError(null);
        setProgress(0);
        setPicked([toLocalMedia(result.assets[0])]);
        return;
      }

      const alreadyImages = picked.filter((m) => m.mediaType === "IMAGE").length;
      const remaining = MAX_IMAGES - alreadyImages;
      if (remaining <= 0) {
        setError(`You can attach up to ${MAX_IMAGES} images.`);
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsMultipleSelection: true,
        selectionLimit: remaining,
        quality: 0.8,
      });
      if (result.canceled || result.assets.length === 0) return;
      setError(null);
      setProgress(0);
      const added = result.assets.map(toLocalMedia);
      setPicked((prev) => {
        const keptImages = prev.filter((m) => m.mediaType === "IMAGE");
        return [...keptImages, ...added].slice(0, MAX_IMAGES);
      });
    },
    [picked]
  );

  /** Remove a single picked item by index. */
  const removeMedia = useCallback((index: number) => {
    setPicked((prev) => prev.filter((_, i) => i !== index));
  }, []);

  /** Drop every picked asset. */
  const clearMedia = useCallback(() => {
    setPicked([]);
    setProgress(0);
  }, []);

  /**
   * Validate, upload every picked asset (reporting aggregate 0..1 progress),
   * then create the post. Returns the created Post on success, or null if
   * validation failed / an error occurred.
   */
  const submit = useCallback(
    async (fields: CreatePostFields): Promise<Post | null> => {
      const errors = validate(fields);
      setFieldErrors(errors);
      if (Object.keys(errors).length > 0) return null;

      setSubmitting(true);
      setError(null);
      try {
        const media: CreatePostMedia[] = [];
        for (let i = 0; i < picked.length; i++) {
          const uploaded = await uploadMedia(picked[i], (fraction) =>
            setProgress((i + fraction) / picked.length)
          );
          media.push({ ...uploaded, posterS3Key: null });
          // `uploaded` already carries mediaType/orientation/s3Key/contentType.
        }
        return await createPost({
          title: fields.title.trim(),
          summary: fields.summary.trim(),
          supportQuestion: fields.supportQuestion.trim(),
          media,
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not publish your post.");
        return null;
      } finally {
        setSubmitting(false);
      }
    },
    [picked]
  );

  return {
    picked,
    progress,
    submitting,
    error,
    fieldErrors,
    pickMedia,
    removeMedia,
    clearMedia,
    submit,
  };
}
