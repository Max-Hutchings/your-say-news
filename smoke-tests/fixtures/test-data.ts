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

export const expectedFeed = {
  video: {
    id: 1046,
    supportQuestion: "Should landlords insulate homes before they can raise the rent?",
    mediaKey: "posts/seed-1046-video.mp4",
  },
  article: {
    id: 1049,
    supportQuestion: "Should battery farms receive priority planning near substations?",
    summary:
      "Developers plan a grid-scale battery farm beside a village substation.",
  },
} as const;
