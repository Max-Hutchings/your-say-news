import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { CharacteristicAnswers } from "../types";

/**
 * Submits a user's characteristic answers.
 *
 * PII separation: the request body carries ONLY the characteristic answers. The
 * authenticated identity travels in the bearer token (attached by YsnHttpClient);
 * we never put userId / name / email alongside the characteristic data.
 */

const extra = Constants.expoConfig?.extra ?? {};
const CHARACTERISTICS_URL =
    `${extra.CHARACTERISTIC_SERVICE_HOST}${extra.CHARACTERISTIC_SERVICE_PORT}/user-characteristics`;

export async function submitCharacteristics(answers: CharacteristicAnswers): Promise<void> {
    await YsnHttpClient.getSecure().post(CHARACTERISTICS_URL, answers);
}
