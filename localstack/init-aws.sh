#!/bin/bash
# Initialize LocalStack resources for post-service, and seed placeholder media so the demo
# feed actually shows images/video. Downloads are best-effort: if the box is offline the
# bucket is still created and the seed post_media rows simply resolve to empty objects.
set -u

BUCKET="post-videos"

echo "Creating S3 bucket for post media..."
awslocal s3 mb "s3://${BUCKET}" 2>/dev/null || true

# Put a placeholder image under a key at the given dimensions. Landscape posts use 1600x900 (16:9),
# portrait posts use 1080x1920 — matching how the feed sizes each orientation.
#   put_image <key> <seed> <width> <height>
put_image() {
  local key="$1" seed="$2" w="${3:-1600}" h="${4:-900}"
  if curl -fsSL "https://picsum.photos/seed/${seed}/${w}/${h}.jpg" -o /tmp/ysn-img.jpg; then
    awslocal s3 cp /tmp/ysn-img.jpg "s3://${BUCKET}/${key}" --content-type image/jpeg >/dev/null
    echo "  seeded ${key} (${w}x${h})"
  else
    echo "  (offline) skipped ${key}"
  fi
}

# Fetch a sample clip into an S3 key. Downloads are cached by URL so many seeded posts can reuse
# the small demo clips without downloading the same file repeatedly. put_video <key> <url>
put_video() {
  local key="$1" url="$2"
  local cache_key cache_file
  cache_key="$(printf '%s' "${url}" | cksum | awk '{print $1}')"
  cache_file="/tmp/ysn-clip-${cache_key}.mp4"
  if [ -s "${cache_file}" ] || curl -fsSL "${url}" -o "${cache_file}"; then
    awslocal s3 cp "${cache_file}" "s3://${BUCKET}/${key}" --content-type video/mp4 >/dev/null
    echo "  seeded ${key}"
  else
    echo "  (offline) skipped ${key}"
  fi
}

VIDEO_CLIP_1="https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_2MB.mp4"
VIDEO_CLIP_2="https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
VIDEO_CLIP_3="https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_2MB.mp4"
VIDEO_CLIP_4="https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

# Seed one video plus a matching deterministic poster. Portrait posts crop the landscape demo clip
# in the feed and use a true 1080x1920 poster; landscape posts use a 1600x900 poster.
#   put_post_video <post-id> <clip-url> <LANDSCAPE|PORTRAIT>
put_post_video() {
  local post_id="$1" clip_url="$2" orientation="$3"
  local width=1600 height=900
  if [ "${orientation}" = "PORTRAIT" ]; then
    width=1080
    height=1920
  fi
  put_video "posts/seed-${post_id}-video.mp4" "${clip_url}"
  put_image "posts/seed-${post_id}-poster.jpg" "ysn-${post_id}-poster" "${width}" "${height}"
}

#   put_post_image <post-id> <LANDSCAPE|PORTRAIT>
put_post_image() {
  local post_id="$1" orientation="$2"
  local width=1600 height=900
  if [ "${orientation}" = "PORTRAIT" ]; then
    width=1080
    height=1920
  fi
  put_image "posts/seed-${post_id}-image.jpg" "ysn-${post_id}-image" "${width}" "${height}"
}

# Post 1000 — five-image landscape carousel
put_image "posts/seed-1-img-1.jpg" "ysn-work-1"
put_image "posts/seed-1-img-2.jpg" "ysn-work-2"
put_image "posts/seed-1-img-3.jpg" "ysn-work-3"
put_image "posts/seed-1-img-4.jpg" "ysn-work-4"
put_image "posts/seed-1-img-5.jpg" "ysn-work-5"

# Post 1001 — landscape video + poster frame
put_video "posts/seed-2-video.mp4" "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_2MB.mp4"
put_image "posts/seed-2-poster.jpg" "ysn-flood-poster"

# Post 1002 — three landscape images
put_image "posts/seed-3-img-1.jpg" "ysn-city-1"
put_image "posts/seed-3-img-2.jpg" "ysn-city-2"
put_image "posts/seed-3-img-3.jpg" "ysn-city-3"

# Post 1004 — single landscape image
put_image "posts/seed-5-img-1.jpg" "ysn-books-1"

# Post 1005 — single PORTRAIT image
put_image "posts/seed-6-img-1.jpg" "ysn-trees-1" 1080 1920

# Post 1006 — LANDSCAPE video (16:9) + matching landscape poster frame
put_video "posts/seed-7-video.mp4" "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
put_image "posts/seed-7-poster.jpg" "ysn-vfarm-poster" 1600 900

# Post 1007 — single PORTRAIT image
put_image "posts/seed-8-img-1.jpg" "ysn-busker-1" 1080 1920

# Post 1008 — LANDSCAPE video (16:9) + matching landscape poster frame
put_video "posts/seed-9-video.mp4" "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_2MB.mp4"
put_image "posts/seed-9-poster.jpg" "ysn-swimmers-poster" 1600 900

# Post 1009 — PORTRAIT video demo. The sample clip is 16:9; the feed fills the tall portrait cell
# with contentFit="cover", and the poster is a genuine 1080x1920 portrait still.
put_video "posts/seed-10-video.mp4" "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"
put_image "posts/seed-10-poster.jpg" "ysn-busker-1" 1080 1920

# Posts 1010-1049 — exact 70% video / 15% image / 15% text-only split across the forty new posts.
# Jane: video 1010-1016, image 1017-1018, text 1019.
put_post_video 1010 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1011 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1012 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1013 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1014 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1015 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1016 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_image 1017 PORTRAIT
put_post_image 1018 LANDSCAPE

# Alice: video 1020-1026, image 1027, text 1028-1029.
put_post_video 1020 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1021 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1022 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1023 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1024 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1025 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1026 "${VIDEO_CLIP_2}" PORTRAIT
put_post_image 1027 LANDSCAPE

# Maya: video 1030-1036, image 1037-1038, text 1039.
put_post_video 1030 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1031 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1032 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1033 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1034 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1035 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1036 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_image 1037 PORTRAIT
put_post_image 1038 LANDSCAPE

# Theo: video 1040-1046, image 1047, text 1048-1049.
put_post_video 1040 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1041 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1042 "${VIDEO_CLIP_4}" PORTRAIT
put_post_video 1043 "${VIDEO_CLIP_1}" LANDSCAPE
put_post_video 1044 "${VIDEO_CLIP_2}" PORTRAIT
put_post_video 1045 "${VIDEO_CLIP_3}" LANDSCAPE
put_post_video 1046 "${VIDEO_CLIP_4}" PORTRAIT
put_post_image 1047 LANDSCAPE

echo "LocalStack initialization complete!"
