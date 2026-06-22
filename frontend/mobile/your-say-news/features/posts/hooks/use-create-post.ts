import { useCallback, useState } from "react";
import * as ImagePicker from "expo-image-picker";
import { createPost } from "../services/PostService";
import { uploadMedia, type LocalMedia } from "../services/MediaUploadService";
import type { CreatePostMedia, MediaType, Post } from "../types";

/**
 * Orchestrates the create-post flow: pick media → presign + upload (with
 * progress) → create the post. Keeps the route thin by owning the picked
 * asset, upload progress, loading and error state in one place.
 */

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

/** Map a picker asset's MIME-ish info to our contentType + mediaType. */
function toLocalMedia(asset: ImagePicker.ImagePickerAsset): LocalMedia {
  const mediaType: MediaType = asset.type === "video" ? "VIDEO" : "IMAGE";
  const contentType =
    asset.mimeType ?? (mediaType === "VIDEO" ? "video/mp4" : "image/jpeg");
  return { uri: asset.uri, mediaType, contentType };
}

export function useCreatePost() {
  const [picked, setPicked] = useState<LocalMedia | null>(null);
  const [progress, setProgress] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<CreatePostErrors>({});

  /** Open the gallery and stash the chosen image/video locally (no upload yet). */
  const pickMedia = useCallback(async (): Promise<void> => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError("We need photo library access to attach media.");
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images", "videos"],
      quality: 0.8,
    });
    if (result.canceled || result.assets.length === 0) return;
    setError(null);
    setProgress(0);
    setPicked(toLocalMedia(result.assets[0]));
  }, []);

  /** Drop the picked asset (e.g. user changed their mind). */
  const clearMedia = useCallback(() => {
    setPicked(null);
    setProgress(0);
  }, []);

  /**
   * Validate, upload any picked media, then create the post. Returns the
   * created Post on success, or null if validation failed / an error occurred.
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
        if (picked) {
          const uploaded = await uploadMedia(picked, setProgress);
          media.push({ ...uploaded, posterS3Key: null });
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
    clearMedia,
    submit,
  };
}
