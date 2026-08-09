/** One entry in the controlled topic catalogue. */
export interface Topic {
  id: string;
  label: string;
  displayGroup: string;
  displayOrder: number;
  active: boolean;
}
