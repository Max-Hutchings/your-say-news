# Stage 2 — Posts: create, store, render

Implementation plan for MVP1 Stage 2 (see `docs/plans/mvp1-roadmap.md`). Built by two parallel
agents — **backend** (`post-service` `posts` domain) and **frontend** (`features/posts` + routes) —
against the shared contract below. The contract is fixed here so the two streams integrate cleanly.

## Decisions locked

- **Media upload: presigned URLs.** `post-service` mints a presigned S3 **PUT** URL; the mobile
  client uploads bytes directly to S3 (LocalStack); the client then sends the resulting **key** in
  the create-post body. On read, the service mints short-lived presigned **GET** URLs so clients can
  view media. Keeps large bytes out of the service and scales.
- **Author from the token, never the body.** `post-service` gains OIDC (mirroring `user-service`).
  The author `userId` is resolved server-side from the authenticated subject's email via
  `user-service` (`GET /your-say-user/email/{email}`), preserving the PII boundary. A create body
  that carries `userId` is ignored.
- **`isUnbiased` always `false` here.** The column/flag exists and renders a badge when true, but
  only the Stage 7 agent sets it.
- **Model build-out.** This is the stage that builds `posts` out, so we migrate the scaffold to the
  real shape (rename `description`→`summary`, drop single `image_url` in favour of a `post_media`
  table, replace `posted_date` with `created_at`/`updated_at`) rather than bolting on.

## Shared API contract (the integration boundary)

All endpoints on `post-service` (`:8082`), under `/posts`, **authenticated** (`@RolesAllowed("user")`),
bearer supplied by the frontend `YsnHttpClient`. JSON unless noted.

### 1. Presign a media upload
`POST /posts/media/presign`
```jsonc
// request
{ "mediaType": "IMAGE" | "VIDEO", "contentType": "image/jpeg" }
// response 200
{ "s3Key": "posts/<uuid>.jpg", "uploadUrl": "<presigned PUT url>", "expiresInSeconds": 900 }
```
Client then `PUT uploadUrl` with the raw file bytes and a matching `Content-Type` header.

### 2. Create a post
`POST /posts` → `201` with `PostDto`
```jsonc
// CreatePostRequest — author derived from token, isUnbiased forced false
{
  "title": "Headline",
  "summary": "Body / summary text",
  "supportQuestion": "Do you agree that ...?",
  "media": [
    { "mediaType": "IMAGE", "s3Key": "posts/abc.jpg", "contentType": "image/jpeg", "posterS3Key": null }
  ]
}
```

### 3. Read endpoints
- `GET /posts/{id}` → `200` `PostDto`, `204` if not found.
- `GET /posts/user/{userId}` → `200` `List<PostDto>` (by author, newest first).
- `GET /posts` → `200` `List<PostDto>` recent newest-first (interim, unranked — Stage 5's
  `FeedRanker` replaces it; lets the home screen show real content now).

### PostDto (response shape)
```jsonc
{
  "id": 123,
  "userId": 1,
  "title": "Headline",
  "summary": "Body / summary text",
  "supportQuestion": "Do you agree that ...?",
  "isUnbiased": false,
  "createdAt": "2026-06-21T10:00:00Z",
  "media": [
    { "mediaType": "IMAGE", "s3Key": "posts/abc.jpg", "contentType": "image/jpeg",
      "posterS3Key": null, "url": "<presigned GET url>", "posterUrl": null }
  ]
}
```
`url` / `posterUrl` are presigned GET URLs minted at read time (not stored).

## Backend workstream (`post-service`)

1. **OIDC + security** — add `quarkus-oidc` (+ test-security) to `build.gradle.kts`; copy the
   `user-service` OIDC/permission config into `application.properties` (`%dev` auth-server-url,
   client id/secret, authenticated permission over `/*` excluding OPTIONS, public `/live` + health).
2. **Model** — `Post` entity: `title`, `summary`, `supportQuestion`, `isUnbiased` (default false),
   `createdAt`/`updatedAt` (`Instant`), `userId`; one-to-many `PostMedia`. New `PostMedia`
   (replace the unused `PostVideo`): `postId`, `mediaType` (IMAGE/VIDEO enum string), `s3Key`,
   `contentType`, `posterS3Key`, `ordinal`, `createdAt`.
3. **Migration** `db/migrations/0002-evolve-post.xml` — alter `post` (add `summary` via rename of
   `description`, `support_question` NOT NULL, `is_unbiased` BOOLEAN default false, `created_at`,
   `updated_at`; drop `image_url`, `posted_date`); create `post_media`. Update the seed changelog
   `0001-seed-posts.xml` to the new columns (+ a couple of `post_media` rows) so it matches the
   final schema. Note: local `compose` recreates volumes; if a changeSet checksum clashes, add
   `<validCheckSum>ANY</validCheckSum>`.
4. **S3 media service** — `MediaStorageService` using `software.amazon.awssdk.services.s3.presigner.S3Presigner`:
   `presignUpload(mediaType, contentType) -> {s3Key, uploadUrl}` (PUT) and `presignDownload(s3Key) -> url`
   (GET, ~15 min). Bucket name from config (`posts.media.bucket`, default the LocalStack bucket in
   `localstack/init-aws.sh`).
5. **Domain wiring** — `PostController` (token-authed): presign, create (author from token email →
   `UserServiceClient.getUserByEmail`), get, list-by-user, recent. `PostService` interface +
   `PostServiceImpl`. New DTOs at the domain top level: `CreatePostRequest`, `PresignRequest`,
   `PresignResponse`, `PostMediaDto`; extend `PostDto`. Keep internals (`model/`, `service/`,
   `client/`) private. Add `getUserByEmail` to `UserServiceClient`.
6. **Tests** — unit: DTO/entity mapping + `isUnbiased`-forced-false + media ordering. Integration
   `@QuarkusTest`: create→get round-trip with media, list-by-author, 204 on missing, author taken
   from token not body (`@TestSecurity` + `@InjectMock UserServiceClient`), presign returns a usable
   key. Use Testcontainers Postgres; mock the `S3Presigner` or use the LocalStack test container.
   Run the `test-audit` skill after.

## Frontend workstream (`features/posts` + routes)

1. **Types** (`features/posts/types.ts`) — `MediaType`, `PostMedia`, `Post`, `CreatePostInput`
   matching the contract (replace the stale `Post` shape).
2. **Services** (`features/posts/services/`) — `PostService.ts` (`createPost`, `getPost`,
   `listByUser`, `getRecent`) and `MediaUploadService.ts` (`presign` then `PUT` bytes with upload
   progress + failure), both on `YsnHttpClient.getSecure()` and `Constants.expoConfig.extra`
   `POST_SERVICE_HOST/PORT` (follow `ConsentService.ts`). Remove the stale `use-posts-api.ts`.
3. **Hooks** — `use-create-post.ts` orchestrating pick → presign → upload (progress) → create, with
   loading/error/progress state.
4. **Components** (`features/posts/components/`, one per file, theme tokens + `@/components/ui`) —
   `PostCard` (media/poster, headline, summary, support question, **unbiased badge** when
   `isUnbiased`), `PostDetail`, and a media picker/upload-progress piece (use `expo-image-picker`;
   `expo-image` for rendering — `bunx expo install` if missing).
5. **Routes** — thin `app/(protected)/create-post.tsx` composing the create flow; register it in
   `(protected)/_layout.tsx`; add a create entry point on home. Replace the placeholder card in
   `(protected)/index.tsx` with real `PostCard`s from `getRecent`. Keep `app/` route-only.
6. **Public face** — export the screen-level pieces from `features/posts/index.ts`.
7. **Tests** (React Testing Library) — `PostCard` renders the fields + shows the badge only when
   unbiased; create-flow validation (required headline/summary/support question) and the upload
   hook's progress/error states.

**Demoable:** a signed-in user creates a headline + summary + support-question post with an image or
video, and views it as a card and on its detail screen.
