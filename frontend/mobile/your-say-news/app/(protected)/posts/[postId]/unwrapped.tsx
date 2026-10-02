import { useLocalSearchParams } from "expo-router";
import { GatedUnwrappedScreen } from "@/features/unwrapped";

export default function PostUnwrappedRoute() {
  const { postId } = useLocalSearchParams<{ postId: string }>();
  return <GatedUnwrappedScreen postId={Number(postId)} />;
}
