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

# Fetch a sample clip into an S3 key. put_video <key> <url>
put_video() {
  local key="$1" url="$2"
  if curl -fsSL "${url}" -o /tmp/ysn-clip.mp4; then
    awslocal s3 cp /tmp/ysn-clip.mp4 "s3://${BUCKET}/${key}" --content-type video/mp4 >/dev/null
    echo "  seeded ${key}"
  else
    echo "  (offline) skipped ${key}"
  fi
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

echo "LocalStack initialization complete!"
