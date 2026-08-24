import { expect, type APIRequestContext } from "@playwright/test";
import type { SignInIdentity } from "../fixtures/test-data";

const AUTH_ORIGIN = process.env.SMOKE_AUTH_ORIGIN ?? "http://localhost:8080";
const API_ORIGIN = process.env.SMOKE_API_ORIGIN ?? "http://localhost:8082";
const REALM = process.env.SMOKE_AUTH_REALM ?? "your-say-news";
const CLIENT_ID = process.env.SMOKE_AUTH_CLIENT_ID ?? "frontend-client";

/**
 * Direct API checks for guards a browser journey cannot prove.
 *
 * A hidden button is an affordance, not an authorisation control: the composer being absent for a
 * plain reader would still pass if the server-side publisher check were deleted. The same applies
 * to results being locked before a vote. These call the endpoints straight, as the real user, and
 * assert the server refuses.
 */
export class ApiGuards {
  constructor(private readonly request: APIRequestContext) {}

  async accessToken(identity: SignInIdentity): Promise<string> {
    const response = await this.request.post(
      `${AUTH_ORIGIN}/realms/${REALM}/protocol/openid-connect/token`,
      {
        form: {
          client_id: CLIENT_ID,
          grant_type: "password",
          username: identity.username,
          password: identity.password,
          scope: "openid profile email",
        },
      }
    );
    expect(response.status(), `${identity.username} should be a seeded account`).toBe(200);
    const { access_token: token } = (await response.json()) as { access_token?: string };
    expect(token).toBeTruthy();
    return token!;
  }

  /** The server, not the UI, must refuse a publish from an account without publisher rights. */
  async expectPublishForbidden(identity: SignInIdentity): Promise<void> {
    const token = await this.accessToken(identity);
    const response = await this.request.post(`${API_ORIGIN}/posts`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        summary: "A reader without publisher rights should never be able to create this.",
        supportQuestion: "Should a plain reader be able to publish?",
        votingType: "BINARY",
      },
    });
    expect(
      response.status(),
      `${identity.username} is not an active official publisher and must be refused`
    ).toBe(403);
  }

  /** Results stay locked until the caller has voted on that post. */
  async expectSentimentLocked(identity: SignInIdentity, postId: number): Promise<void> {
    const token = await this.accessToken(identity);
    const response = await this.request.get(`${API_ORIGIN}/votes/${postId}/sentiment`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(
      response.status(),
      `${identity.username} has not voted on post ${postId} yet, so results must be refused`
    ).toBe(403);
  }
}
