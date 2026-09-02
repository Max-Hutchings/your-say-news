/** One reusable entry in the governed topic tag catalogue. */
export interface TopicTag {
  id: string;
  label: string;
  displayGroup: string;
  displayOrder: number;
  active: boolean;
}
