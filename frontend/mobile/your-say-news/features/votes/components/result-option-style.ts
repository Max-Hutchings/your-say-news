import { getEditorial } from "@/constants/theme";
import type { ResultVoteOption } from "../types";

export function optionColor(option: ResultVoteOption, index: number, e: ReturnType<typeof getEditorial>) {
  if (option.semanticKey === "AGREE") return e.voteAgreeFill;
  if (option.semanticKey === "DISAGREE") return e.coral;
  return [e.teal, e.coral, e.lime, e.secondary, e.muted][index % 5];
}

export function formatPct(pct: number) {
  return Number.isInteger(pct) ? String(pct) : pct.toFixed(1);
}
