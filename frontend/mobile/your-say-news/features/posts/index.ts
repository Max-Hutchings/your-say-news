/**
 * Posts feature — public face.
 *
 * Routes and other features import posts ONLY from here, never from the
 * internal services/, hooks/ or non-screen components.
 */

export { HomeFeed } from "./components/HomeFeed";
export { CreatePostScreen } from "./components/CreatePostScreen";
export { PostCard } from "./components/PostCard";

export type { Post, PostMedia, MediaType, CreatePostInput } from "./types";
