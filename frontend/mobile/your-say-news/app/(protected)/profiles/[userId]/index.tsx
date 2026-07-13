import { useLocalSearchParams } from "expo-router";
import { ProfileScreen } from "@/features/profiles";

export default function UserProfileRoute() {
  const { userId } = useLocalSearchParams<{ userId: string }>();
  return <ProfileScreen userId={Number(userId)} />;
}
