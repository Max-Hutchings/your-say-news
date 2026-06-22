import axios from "axios";
import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { MediaType, PresignRequest, PresignResponse } from "../types";

/**
 * Two-step media upload: ask post-service for a presigned S3 PUT URL, then send
 * the raw file bytes straight to S3 (LocalStack). Keeping the bytes off the
 * service is the whole point of presigning, so the PUT uses a bare axios client
 * with NO bearer — the presigned URL is self-authenticating, and attaching our
 * token would break the S3 signature.
 */

const extra = Constants.expoConfig?.extra ?? {};
const PRESIGN_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/posts/media/presign`;

/** A local asset the user picked, ready to upload. */
export interface LocalMedia {
  /** Local file URI from expo-image-picker (file://...). */
  uri: string;
  mediaType: MediaType;
  contentType: string;
}

/** Result of a completed upload — the key to attach to the create-post body. */
export interface UploadedMedia {
  mediaType: MediaType;
  s3Key: string;
  contentType: string;
}

/** Ask the service for a presigned PUT URL and the key it will live under. */
export async function presign(request: PresignRequest): Promise<PresignResponse> {
  const { data } = await YsnHttpClient.getSecure().post<PresignResponse>(PRESIGN_URL, request);
  return data;
}

/**
 * Presign + upload a single local asset to S3, reporting 0..1 progress.
 * Throws on failure (network or non-2xx from S3) so the caller can surface it.
 */
export async function uploadMedia(
  media: LocalMedia,
  onProgress?: (fraction: number) => void
): Promise<UploadedMedia> {
  const { s3Key, uploadUrl } = await presign({
    mediaType: media.mediaType,
    contentType: media.contentType,
  });

  // Read the local file into a blob so axios can stream it to S3.
  const fileResponse = await fetch(media.uri);
  const blob = await fileResponse.blob();

  await axios.put(uploadUrl, blob, {
    headers: { "Content-Type": media.contentType },
    onUploadProgress: (event) => {
      if (!onProgress) return;
      const fraction = event.total ? event.loaded / event.total : 0;
      onProgress(Math.min(1, fraction));
    },
  });

  onProgress?.(1);
  return { mediaType: media.mediaType, s3Key, contentType: media.contentType };
}
