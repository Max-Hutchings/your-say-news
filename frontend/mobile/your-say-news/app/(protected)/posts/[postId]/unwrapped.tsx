import { useLocalSearchParams } from "expo-router";
import { UnwrappedScreen } from "@/features/unwrapped";

export default function PostUnwrappedRoute() {
  const { postId } = useLocalSearchParams<{ postId: string }>();
  return <UnwrappedScreen postId={Number(postId)} />;
}
