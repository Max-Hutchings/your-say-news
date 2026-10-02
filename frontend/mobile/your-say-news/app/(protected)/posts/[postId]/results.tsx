import { useLocalSearchParams } from "expo-router";
import { PostResultsScreen } from "@/features/unwrapped";

export default function PostResultsRoute() {
  const { postId } = useLocalSearchParams<{ postId: string }>();
  return <PostResultsScreen postId={Number(postId)} />;
}
