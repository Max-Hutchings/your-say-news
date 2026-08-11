import type { AdminTopic } from "../types";

type TopicLedgerProps = {
  topics: AdminTopic[];
  savingIds: Set<string>;
  onSetActive: (topicId: string, active: boolean) => Promise<AdminTopic>;
};

export function TopicLedger({ topics, savingIds, onSetActive }: TopicLedgerProps) {
  const setActive = async (topicId: string, active: boolean) => {
    try {
      await onSetActive(topicId, active);
    } catch {
      // The controlled checkbox rolls back from hook state; the page banner gives retry guidance.
    }
  };

  return (
    <div className="topic-ledger">
      <div className="topic-ledger__heading" aria-hidden="true">
        <span>Topic tag</span><span>Group</span><span>Order</span><span>Status</span>
      </div>
      <ol>
        {topics.map((topic) => (
          <li key={topic.id} className={topic.active ? "topic-row" : "topic-row topic-row--retired"}>
            <div><strong>{topic.label}</strong><code>{topic.id}</code></div>
            <span>{topic.displayGroup}</span>
            <span className="topic-row__order">{String(topic.displayOrder).padStart(2, "0")}</span>
            <label className="topic-active">
              <input
                type="checkbox"
                aria-label={`Topic tag active for ${topic.label}`}
                checked={topic.active}
                disabled={savingIds.has(topic.id)}
                onChange={(event) => void setActive(topic.id, event.target.checked)}
              />
              <span>{savingIds.has(topic.id) ? "Saving" : topic.active ? "Active" : "Retired"}</span>
            </label>
          </li>
        ))}
      </ol>
    </div>
  );
}
