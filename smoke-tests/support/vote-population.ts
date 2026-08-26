import { expect, type APIRequestContext } from "@playwright/test";
import { votePopulation } from "../fixtures/test-data";

const AUTH_ORIGIN = process.env.SMOKE_AUTH_ORIGIN ?? "http://localhost:8080";
const API_ORIGIN = process.env.SMOKE_API_ORIGIN ?? "http://localhost:8082";

type VoteOption = {
  id: number;
  label: string;
  semanticKey: string | null;
};

/**
 * Casts the seeded twenty-account population's votes on a post.
 *
 * This is deliberately *setup*, not the journey under test. Driving twenty Firebase sign-ins
 * through the browser would add minutes to a serial suite and make the population the most
 * fragile thing in it, while proving nothing the single-voter journeys do not already prove. The
 * votes still travel the real path — a real token per account, the real `POST /votes` endpoint,
 * the real characteristic snapshot — so the aggregate the browser then reads is genuinely
 * produced by the system. ADR-030 keeps the browser steps for what is actually being verified:
 * a reader seeing that aggregate.
 */
export class VotePopulation {
  constructor(private readonly request: APIRequestContext) {}

  /** Casts all twenty votes and returns the option ids used, so a caller can assert against them. */
  async castAllVotes(postId: number): Promise<{ agreeOptionId: number; disagreeOptionId: number }> {
    const { agreeOptionId, disagreeOptionId } = await this.binaryOptions(postId);

    for (const [index, username] of votePopulation.usernames.entries()) {
      const stance = votePopulation.stances[index];
      await this.castVote({
        username,
        postId,
        optionId: stance === "AGREE" ? agreeOptionId : disagreeOptionId,
      });
    }

    return { agreeOptionId, disagreeOptionId };
  }

  private async binaryOptions(
    postId: number
  ): Promise<{ agreeOptionId: number; disagreeOptionId: number }> {
    // `PostController` is @RolesAllowed("user"), so even reading a post needs a bearer token.
    const token = await this.accessToken(votePopulation.usernames[0]);
    const response = await this.request.get(`${API_ORIGIN}/posts/${postId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(
      response.status(),
      `post ${postId} must be seeded for the voting journeys`
    ).toBe(200);

    const { voteOptions } = (await response.json()) as { voteOptions: VoteOption[] };
    const agree = voteOptions.find((option) => option.semanticKey === "AGREE");
    const disagree = voteOptions.find((option) => option.semanticKey === "DISAGREE");
    expect(
      agree && disagree,
      `post ${postId} must expose AGREE and DISAGREE options`
    ).toBeTruthy();

    return { agreeOptionId: agree!.id, disagreeOptionId: disagree!.id };
  }

  private async castVote({
    username,
    postId,
    optionId,
  }: {
    username: string;
    postId: number;
    optionId: number;
  }): Promise<void> {
    const token = await this.accessToken(username);
    const response = await this.request.post(`${API_ORIGIN}/votes`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { postId, optionId },
    });
    expect(response.status(), `${username} should record a vote on post ${postId}`).toBe(201);
  }

  private async accessToken(username: string): Promise<string> {
    const response = await this.request.post(
      `${AUTH_ORIGIN}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=local`,
      {
        data: {
          email: `${username}@example.com`,
          password: votePopulation.password,
          returnSecureToken: true,
        },
      }
    );
    expect(response.status(), `${username} should be a seeded account`).toBe(200);

    const { idToken: accessToken } = (await response.json()) as {
      idToken?: string;
    };
    expect(accessToken, `${username} should receive an access token`).toBeTruthy();
    return accessToken!;
  }
}
