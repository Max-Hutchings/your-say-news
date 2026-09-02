import { useLocalSearchParams } from "expo-router";
import { PostScreen } from "@/features/posts";

export default function SharedPostRoute() {
  const { postId } = useLocalSearchParams<{ postId: string }>();
  return <PostScreen postId={Number(postId)} />;
}
