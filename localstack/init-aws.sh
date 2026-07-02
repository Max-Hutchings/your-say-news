#!/bin/bash
# Initialize LocalStack resources for post-service, and seed placeholder media so the demo
# feed actually shows images/video. Downloads are best-effort: if the box is offline the
# bucket is still created and the seed post_media rows simply resolve to empty objects.
set -u

BUCKET="post-videos"

echo "Creating S3 bucket for post media..."
awslocal s3 mb "s3://${BUCKET}" 2>/dev/null || true

# Put a placeholder portrait image (1080x1920, matches the full-screen feed) under a key.
put_image() {
  local key="$1" seed="$2"
  if curl -fsSL "https://picsum.photos/seed/${seed}/1080/1920.jpg" -o /tmp/ysn-img.jpg; then
    awslocal s3 cp /tmp/ysn-img.jpg "s3://${BUCKET}/${key}" --content-type image/jpeg >/dev/null
    echo "  seeded ${key}"
  else
    echo "  (offline) skipped ${key}"
  fi
}

# Post 1000 — five-image carousel
put_image "posts/seed-1-img-1.jpg" "ysn-work-1"
put_image "posts/seed-1-img-2.jpg" "ysn-work-2"
put_image "posts/seed-1-img-3.jpg" "ysn-work-3"
put_image "posts/seed-1-img-4.jpg" "ysn-work-4"
put_image "posts/seed-1-img-5.jpg" "ysn-work-5"

# Post 1001 — video + poster frame
if curl -fsSL "https://download.samplelib.com/mp4/sample-10s.mp4" -o /tmp/ysn-clip.mp4; then
  awslocal s3 cp /tmp/ysn-clip.mp4 "s3://${BUCKET}/posts/seed-2-video.mp4" --content-type video/mp4 >/dev/null
  echo "  seeded posts/seed-2-video.mp4"
else
  echo "  (offline) skipped posts/seed-2-video.mp4"
fi
put_image "posts/seed-2-poster.jpg" "ysn-flood-poster"

# Post 1002 — three images
put_image "posts/seed-3-img-1.jpg" "ysn-city-1"
put_image "posts/seed-3-img-2.jpg" "ysn-city-2"
put_image "posts/seed-3-img-3.jpg" "ysn-city-3"

# Post 1004 — single image
put_image "posts/seed-5-img-1.jpg" "ysn-books-1"

echo "LocalStack initialization complete!"
