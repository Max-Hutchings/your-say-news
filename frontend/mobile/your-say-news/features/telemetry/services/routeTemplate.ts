/**
 * Screen name = expo-router route template, e.g. ["(protected)", "posts", "[postId]"] becomes
 * "/posts/[postId]". Templates (not URLs) keep the metric label set bounded.
 */
export function screenFromSegments(segments: readonly string[]): string {
    const visible = segments.filter((segment) => !isRouteGroup(segment) && segment !== "index");
    return `/${visible.join("/")}`;
}

/**
 * The one route parameter worth logging: which story a screen showed. Profile user ids are left out
 * on purpose, so a session journey never names another member.
 */
export function screenTarget(params: Record<string, string | string[] | undefined>): string | undefined {
    const postId = params.postId;
    return typeof postId === "string" && /^\d{1,19}$/.test(postId) ? postId : undefined;
}

/** "https://dev.yoursaynews.com/api/posts/42?x=1" -> "/api/posts/42". The server strips "/api". */
export function pathOf(url: string | undefined): string {
    if (!url) {
        return "/";
    }
    const withoutOrigin = url.replace(/^[a-z][a-z0-9+.-]*:\/\/[^/]+/i, "");
    const path = withoutOrigin.split(/[?#]/)[0];
    return path.startsWith("/") ? path : `/${path}`;
}

/**
 * A request path with every id-like segment replaced by "{id}", so a profile id, story id or email
 * never leaves the device. post-service applies its own route allowlist on top.
 */
export function templatePath(url: string | undefined): string {
    return pathOf(url)
        .split("/")
        .map((segment) => (isIdLike(segment) ? "{id}" : segment))
        .join("/");
}

function isIdLike(segment: string): boolean {
    return /^\d+$/.test(segment)
        || /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(segment)
        || segment.length >= 20
        || segment.includes("@")
        || segment.includes("%40");
}

function isRouteGroup(segment: string): boolean {
    return segment.startsWith("(") && segment.endsWith(")");
}
