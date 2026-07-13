import { useLocalSearchParams } from "expo-router";
import { ConnectionsScreen, type ConnectionsTab } from "@/features/profiles";

export default function ConnectionsRoute() {
  const { userId, tab } = useLocalSearchParams<{ userId: string; tab?: string }>();
  const initialTab: ConnectionsTab = tab === "following" ? "following" : "followers";
  return <ConnectionsScreen userId={Number(userId)} initialTab={initialTab} />;
}
