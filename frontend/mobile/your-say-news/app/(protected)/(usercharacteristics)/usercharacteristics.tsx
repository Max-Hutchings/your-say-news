/**
 * Onboarding route — the characteristics wizard, guarded. All wizard logic lives in the feature;
 * this wrapper only guards entry: a user who already has a characteristic profile (e.g. they typed
 * this URL directly) is sent to the feed rather than allowed to re-run the wizard.
 */
import { Redirect, type Href } from "expo-router";
import { useAuthStore } from "@/features/auth";
import { OnboardingScreen } from "@/features/user-characteristics";

export default function UserCharacteristicsRoute() {
  const hasCharacteristics = useAuthStore((s) => s.hasCharacteristics);

  if (hasCharacteristics) {
    return <Redirect href={"/(protected)" as Href} />;
  }

  return <OnboardingScreen />;
}
