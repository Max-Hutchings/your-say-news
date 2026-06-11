# Service Layer with Interfaces + DTOs — Design

**Date:** 2026-06-11
**Status:** Approved (design phase)

## Problem

The backend REST controllers talk **directly to the repositories** and also carry business
logic and entity-shaped request/response bodies. This violates the DDD layering in `CLAUDE.md`:
controllers should be thin, a service interface should sit between the controller and the
repository, and entities should never cross the domain boundary.

This affects all four domains across the two services:

| Service | Domain | Controller | Repository |
|---------|--------|-----------|-----------|
| post-service | `posts` | `PostController` | `PostRepository` (reactive, `Uni`) |
| post-service | `votes` | `VoteController` | `VoteRepository` (reactive, `Uni`) |
| user-service | `user` | `YourSayUserController` | `YourSayUserRepository` (blocking, virtual threads) |
| user-service | `usercharacteristic` | `UserCharacteristicController` | `UserCharacteristicRepository` (blocking) |

## Goals

1. Insert a **service layer** between every controller and its repository.
2. Each service is a **public Java interface** at the domain's top level; the controller and
   any other package depend **only on the interface**. The implementation is internal.
3. **Move all business logic** out of the controllers into the service implementations.
   Web-only concerns (JWT / `SecurityIdentity` extraction) stay in the controllers and pass
   plain values down.
4. Introduce **DTOs (records)** for every entity that crosses the service boundary, in **both
   directions** (request DTOs in, response DTOs out). Entities stay private to `model/`.

## Non-Goals

- No new endpoints or behavior changes beyond the two latent bugs fixed as fallout (below).
- No DTOs for `PostVideo` or `VoteCharacteristicTotal` — neither is returned or accepted by any
  endpoint today (YAGNI).
- No change to the reactive-vs-blocking style of any domain; each service preserves its
  domain's existing concurrency model.

## Target structure (per domain, per `CLAUDE.md`)

```
com.yoursay.<domain>/
  <Domain>Controller.java        <- THIN: parse request -> call interface -> return
  <Domain>Service.java           <- PUBLIC INTERFACE (top level, the only cross-boundary type)
  <Domain>Dto.java               <- response DTO record (public face)
  <request DTO records>          <- e.g. CreatePostRequest (public face)
  service/
    <Domain>ServiceImpl.java     <- @ApplicationScoped implementation, injects the repository
    <Domain>Mapper.java          <- entity <-> DTO mapping (internal)
  model/                         <- entities + repository (unchanged except fixes below)
```

- Controllers inject the **interface** (`@Inject <Domain>Service`); CDI wires the impl.
- The repository is referenced **only** by the impl.
- DTOs are **Java records**. Mapping lives in internal `service/<Domain>Mapper` classes so the
  DTO records stay pure data and the entity stays private to `model/`.

## DTOs

### posts
- `PostDto(Long id, Long userId, String title, String description, LocalDate postedDate, String imageUrl)`
- `CreatePostRequest(Long userId, String title, String description, String imageUrl)`

### votes
- `VoteDto(Long id, Long postId, boolean voteFor, Long userId)`
- `CreateVoteRequest(Long postId, boolean voteFor, Long userId)`

### user
- `YourSayUserDto(Long id, String email, String firstName, String lastName, LocalDate dateOfBirth)`
  — **drops internal `createdDate` and `active`**.
- `RegisterUserRequest(String email, String firstName, String lastName, LocalDate dateOfBirth)`
  — assembled by the controller from JWT claims (`email`, `given_name`, `family_name`) plus the
  request body's `birthDate`.

### usercharacteristic
- `UserCharacteristicDto(Long id, Long userId, String postcode, UKCounty ukCounty, Race raceEnum,
  IncomeRange incomeRangeEnum, CountryOfBirth countryOfBirthEnum,
  PoliticalPersuasion politicalPersuasionEnum, SexAtBirth sexAtBirthEnum, Height heightEnum,
  EyeColor eyeColorEnum, WeightRange weightRangeEnum, Parent parentEnum, boolean universityEducated,
  UniversitySubject universitySubjectEnum, boolean propertyOwner)`
- `SaveUserCharacteristicRequest(...)` — same fields **without `id`**.

### PII boundary

`YourSayUserDto` and `UserCharacteristicDto` are the **single chokepoint** for what leaves the
service. `postcode` stays on the characteristic DTO for now because the only endpoint is the
self-service "by userId" lookup — but any future aggregate / cross-user endpoint must strip
`postcode` (and never join characteristics to `YourSayUserDto` identity) at this boundary. This
is the heart of the product rule in `CLAUDE.md`: aggregate, anonymised sentiment, never PII
alongside a vote.

## Service interfaces & moved logic

### `PostService`
```java
Uni<PostDto> savePost(CreatePostRequest request);
Uni<PostDto> getPost(Long id);
Uni<List<PostDto>> getUserPosts(Long userId);
```
Impl injects `PostRepository` **and** `@RestClient UserServiceClient`. It owns the
user-existence check, the HTTP 204 "user not found" handling (returns a null item, preserving
current behavior), all `Log` calls, and entity↔DTO mapping. `PostController` drops to three
one-liners.

### `VoteService`
```java
Uni<List<VoteDto>> getPostVotes(Long postId);
Uni<VoteDto> postVote(CreateVoteRequest request);
```
Impl injects `VoteRepository`, owns logging and mapping.

### `YourSayUserService`
```java
YourSayUserDto findOrCreateUser(String email, String firstName, String lastName);
YourSayUserDto saveUser(RegisterUserRequest request);
YourSayUserDto getUserById(long id);
YourSayUserDto getUserByEmail(String email);
```
The controller keeps JWT / `SecurityIdentity` extraction and the "claims present" validation
(web concerns), then passes plain values in. The **find-or-create** logic and logging move into
the impl. The `GET /your-say-user` route calls `findOrCreateUser`; `GET /your-say-user/data`
stays a trivial controller-only health echo (no service).

### `UserCharacteristicService`
```java
UserCharacteristicDto getCharacteristicByUserId(long userId);
UserCharacteristicDto saveUserCharacteristic(SaveUserCharacteristicRequest request);
```
Method named to reflect actual behavior (the existing controller path `/{id}` queries by
**userId** via `getUserCharacteristicByUserId`); the public endpoint path is unchanged.

## Latent bugs fixed as fallout

1. `VoteRepository` has **no `@ApplicationScoped`** — it cannot be injected. Add it.
2. `UserCharacteristicController` **never injects** its repository (field is unset → NPE at
   runtime). The new `UserCharacteristicServiceImpl` injects the repository properly via
   `@Inject`.

## Testing

- The existing `PostControllerTest` (Quarkus + Testcontainers) remains the end-to-end source of
  truth and must still pass. It will need updating for the new DTO request/response shapes.
- Add focused service tests where the impl now owns real logic worth pinning:
  - `posts`: the user-exists vs HTTP-204 branch of `savePost`, and mapping.
  - `user`: `findOrCreateUser` returns the existing user when present and creates when absent.
- Assertions pin **expected values**, not "not null". Run the `test-audit` skill after writing
  tests.

## Suggested implementation order

1. `votes` — simplest; establishes the interface + DTO + mapper + impl pattern (and fixes the
   `@ApplicationScoped` bug).
2. `posts` — adds the `UserServiceClient` orchestration and updates `PostControllerTest`.
3. `usercharacteristic` — adds the injection fix.
4. `user` — JWT extraction stays in controller, find-or-create moves down.
