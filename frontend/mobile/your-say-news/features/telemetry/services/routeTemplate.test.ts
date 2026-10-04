import { pathOf, screenFromSegments, screenTarget, templatePath } from "./routeTemplate";

describe("screenFromSegments", () => {
    it.each([
        [[], "/"],
        [["(protected)"], "/"],
        [["sign-in"], "/sign-in"],
        [["(protected)", "posts", "[postId]"], "/posts/[postId]"],
        [["(protected)", "posts", "[postId]", "results"], "/posts/[postId]/results"],
        [["(protected)", "(usercharacteristics)", "usercharacteristics"], "/usercharacteristics"],
        [["(protected)", "profiles", "[userId]", "index"], "/profiles/[userId]"],
    ])("%j -> %s", (segments, expected) => {
        expect(screenFromSegments(segments)).toBe(expected);
    });
});

describe("screenTarget", () => {
    it("keeps a numeric story id", () => {
        expect(screenTarget({ postId: "42" })).toBe("42");
    });

    it("never reports a profile user id", () => {
        expect(screenTarget({ userId: "17" })).toBeUndefined();
    });

    it("drops a malformed or repeated story id", () => {
        expect(screenTarget({ postId: "42; drop" })).toBeUndefined();
        expect(screenTarget({ postId: ["1", "2"] })).toBeUndefined();
    });
});

describe("templatePath", () => {
    it.each([
        ["https://dev.yoursaynews.com/api/posts/42/unwrapped/3f2c1a9e-8b7d-4c6e-9a5f-1d2e3f4a5b6c/follow-up",
            "/api/posts/{id}/unwrapped/{id}/follow-up"],
        ["/feed?cursor=abc", "/feed"],
        ["/votes/7/sentiment/ageRange", "/votes/{id}/sentiment/ageRange"],
    ])("%s -> %s", (url, expected) => {
        expect(templatePath(url)).toBe(expected);
    });
});

describe("pathOf", () => {
    it.each([
        ["https://dev.yoursaynews.com/api/posts/42?include=sources", "/api/posts/42"],
        ["http://localhost:8082/votes/7/sentiment#chart", "/votes/7/sentiment"],
        ["/feed?cursor=abc", "/feed"],
        ["feed", "/feed"],
        [undefined, "/"],
    ])("%s -> %s", (url, expected) => {
        expect(pathOf(url)).toBe(expected);
    });
});
