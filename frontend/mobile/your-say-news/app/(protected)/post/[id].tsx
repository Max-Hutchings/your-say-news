/**
 * Post-detail route — thin wrapper. Reads the id from the path and hands it to
 * the posts feature; all loading/rendering logic lives there.
 */
import { useLocalSearchParams } from "expo-router";
import { PostDetailScreen } from "@/features/posts";

export default function PostDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <PostDetailScreen id={Number(id)} />;
}
