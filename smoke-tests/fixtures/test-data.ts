import { resolve } from "node:path";

export type RegistrationIdentity = {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
};

export type SignInIdentity = {
  username: string;
  email: string;
  password: string;
};

export const returningReader: SignInIdentity = {
  username: process.env.SMOKE_READER_USERNAME ?? "riley.reader",
  email:
    process.env.SMOKE_READER_EMAIL ?? "riley.reader@example.com",
  password: process.env.SMOKE_READER_PASSWORD ?? "password123",
};

/**
 * Publishing is not open to every signed-in account: `PostServiceImpl` rejects an author who is
 * not an active official publisher. Maya is seeded OFFICIAL/ACTIVE and, unlike John, is not also
 * an administrator, so the compose journeys exercise publishing without admin privileges.
 */
export const publisherAccount: SignInIdentity = {
  username: process.env.SMOKE_PUBLISHER_USERNAME ?? "maya.patel",
  email:
    process.env.SMOKE_PUBLISHER_EMAIL ?? "maya.patel@example.com",
  password: process.env.SMOKE_PUBLISHER_PASSWORD ?? "password123",
};

export const adminAccount: SignInIdentity = {
  username: process.env.SMOKE_ADMIN_USERNAME ?? "yoursay.admin",
  email: process.env.SMOKE_ADMIN_EMAIL ?? "admin@yoursay.com",
  password: process.env.SMOKE_ADMIN_PASSWORD ?? "password123",
};

export const managedAccount = {
  id: 9,
  displayName: "Casey Morgan",
  email: "casey.morgan@example.com",
  firstName: "Casey",
  lastName: "Morgan",
  createdDate: "2024-06-05",
  initialAccountType: "USER",
  changedAccountType: "OFFICIAL",
  initialActive: true,
} as const;

/**
 * Follow targets for the social journeys.
 *
 * Each names a seeded post they author, because a profile is opened by tapping that post's author
 * in the feed. Riley starts following Maya (seeded) and not following Theo, so each direction has
 * an independent precondition and neither journey depends on the other having run.
 *
 * Following a post author does reorder the feed — the ranker boosts followed authors — which is
 * why no journey asserts on feed position any more.
 */
export const followTarget = {
  id: 8,
  displayName: "Theo Campbell",
  handle: "theo.campbell",
  postId: 2004,
} as const;

export const unfollowTarget = {
  id: 7,
  displayName: "Maya Patel",
  handle: "maya.patel",
  postId: 2007,
} as const;

/** Seeded posts the feed journey reads. Authored under `0010-seed-curated-posts.xml`. */
export const expectedFeed = {
  video: {
    id: 2003,
    supportQuestion: "What should the city prioritise when the bus-fare trial ends?",
    mediaKey: "posts/seed-2003-video.mp4",
  },
  article: {
    id: 2007,
    supportQuestion: "Should we lower public spending to lower income tax?",
    summary:
      "The contradiction is that this expensive state can still feel absent",
  },
} as const;

/**
 * The two posts reserved for the voting journeys (`0014-seed-smoke-journey-posts.xml`).
 *
 * They carry no seeded votes, so the population journey can assert exact tallies, and they stay
 * far below the hundred-vote Unwrapped milestone, so no Grok generation is ever triggered.
 */
export const votingPosts = {
  population: {
    id: 2100,
    supportQuestion:
      "Should every school receive the same repair budget regardless of building age?",
  },
  resultsAccess: {
    id: 2101,
    supportQuestion: "Should public libraries lend household tools as well as books?",
  },
} as const;

/**
 * The seeded twenty-account voting population (`0010-seed-smoke-vote-population.yaml`), and the
 * tallies their political leaning is designed to produce. The gradient is deliberate: every
 * left-leaning voter agrees, every right-leaning voter disagrees, and the middle splits — so a
 * breakdown assertion pins real numbers instead of "a chart rendered".
 */
export const votePopulation = {
  password: process.env.SMOKE_VOTER_PASSWORD ?? "password123",
  usernames: Array.from(
    { length: 20 },
    (_, index) => `smoke.voter.${String(index + 1).padStart(2, "0")}`
  ),
  /** Stance each voter casts, indexed to match `usernames`. */
  stances: [
    "AGREE", "AGREE", "AGREE", "AGREE",
    "AGREE", "AGREE", "AGREE", "DISAGREE",
    "AGREE", "AGREE", "DISAGREE",
    "AGREE", "DISAGREE", "DISAGREE", "DISAGREE",
    "DISAGREE", "DISAGREE", "DISAGREE",
    "AGREE", "DISAGREE",
  ] as const,
  /**
   * What the reader sees after casting their own vote on top of the population.
   *
   * Riley is seeded NOT_POLITICAL and votes Agree, so that one bucket gains an agree and the
   * headline split becomes 12 / 9 of 21. The gradient across the other buckets is untouched, which
   * is exactly what makes the assertion worth making: a broken aggregation would not preserve it.
   */
  expectedTotalsWithReader: { agree: 12, disagree: 9, total: 21 },
  expectedPoliticalBreakdownWithReader: [
    { bucket: "LEFT", label: "Left", agree: 4, disagree: 0 },
    { bucket: "CENTRE_LEFT", label: "Centre Left", agree: 3, disagree: 1 },
    { bucket: "CENTRE", label: "Centre", agree: 2, disagree: 1 },
    { bucket: "CENTRE_RIGHT", label: "Centre Right", agree: 1, disagree: 3 },
    { bucket: "RIGHT", label: "Right", agree: 0, disagree: 3 },
    { bucket: "NOT_POLITICAL", label: "Not Political", agree: 2, disagree: 1 },
  ],
} as const;

/** Local media the compose journeys upload. Committed so a publish run needs no network. */
export const composeMedia = {
  image: resolve(__dirname, "media/smoke-photo.jpg"),
  video: resolve(__dirname, "media/smoke-clip.mp4"),
} as const;

export function newRegistrationIdentity(): RegistrationIdentity {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return {
    username: `smoke.reader.${suffix}`,
    email: `smoke.reader.${suffix}@example.com`,
    firstName: "Morgan",
    lastName: "Tester",
    password: "LocalSmoke!2026",
  };
}

/** A support question unique to this run, so a published post is unambiguous in the feed. */
export function newPostContent(kind: "image" | "video" | "text") {
  const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
  return {
    supportQuestion: `Should the ${kind} smoke run ${suffix} be published?`,
    summary:
      `A ${kind} post published by the smoke suite to prove the compose flow reaches the ` +
      `feed. Run marker ${suffix}.`,
  };
}
