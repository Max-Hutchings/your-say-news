export interface AdminTopic {
  id: string;
  label: string;
  displayGroup: string;
  displayOrder: number;
  active: boolean;
}

export interface CreateTopicInput {
  label: string;
  displayGroup: string;
}
