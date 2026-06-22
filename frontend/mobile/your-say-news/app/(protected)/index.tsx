/**
 * Home route — thin wrapper. The consent gate stays here (first-time users
 * must accept the privacy promise); the feed itself lives in the posts feature.
 */
import { Redirect, type Href } from "expo-router";
import { useAuthStore } from "@/features/auth";
import { HomeFeed } from "@/features/posts";

export default function Home() {
  const { consentedAt } = useAuthStore();

  // First-time users must read the privacy promise and consent before anything else.
  if (!consentedAt) {
    // Cast: typed-routes regenerate the "/consent" entry on the next `expo start`.
    return <Redirect href={"/consent" as Href} />;
  }

  return <HomeFeed />;
}
