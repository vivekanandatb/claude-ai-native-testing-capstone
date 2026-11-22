// Display labels for course levels. Kept as an explicit map rather than a
// derived string so the wording can be tuned per region.
export const LEVEL_LABELS = {
  FOUNDATION: "Foundation",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
};

export const LEVEL_FILTERS = ["FOUNDATION", "INTERMEDIATE", "ADVANCED"];

export function formatLevel(level) {
  return LEVEL_LABELS[level] ?? level;
}

export function formatCredits(credits) {
  if (credits === 1) {
    return "1 credit";
  }
  return `${credits} credits`;
}
