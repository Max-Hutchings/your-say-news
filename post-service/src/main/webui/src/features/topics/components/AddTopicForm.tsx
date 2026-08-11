import { useEffect, useMemo, useState, type FormEvent } from "react";
import type { AdminTopic, CreateTopicInput } from "../types";

type AddTopicFormProps = {
  topics: AdminTopic[];
  adding: boolean;
  onAdd: (input: CreateTopicInput) => Promise<AdminTopic>;
};

export function AddTopicForm({ topics, adding, onAdd }: AddTopicFormProps) {
  const groups = useMemo(() => [...new Set(topics.map((topic) => topic.displayGroup))], [topics]);
  const [label, setLabel] = useState("");
  const [displayGroup, setDisplayGroup] = useState("");
  useEffect(() => {
    if (!displayGroup && groups[0]) setDisplayGroup(groups[0]);
  }, [displayGroup, groups]);
  const idPreview = canonicalTopicId(label);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!label.trim() || !displayGroup) return;
    try {
      await onAdd({ label: label.trim(), displayGroup });
      setLabel("");
    } catch {
      // The page-level error banner explains the failure and offers a retry. Keep the form values.
    }
  };

  return (
    <form className="topic-add" onSubmit={(event) => void submit(event)}>
      <div className="topic-add__heading">
        <div>
          <p>Catalogue control</p>
          <h2>Add a topic tag</h2>
        </div>
        <span>New topic tags join the end of the reader menu.</span>
      </div>
      <div className="topic-add__fields">
        <label>
          <span>Label</span>
          <input value={label} maxLength={80} onChange={(event) => setLabel(event.target.value)} placeholder="Public transport" />
        </label>
        <label>
          <span>Display group</span>
          <select value={displayGroup} onChange={(event) => setDisplayGroup(event.target.value)}>
            {groups.map((group) => <option key={group} value={group}>{group}</option>)}
          </select>
        </label>
        <div className="topic-add__id" aria-label="Topic tag ID">
          <span>Canonical ID</span>
          <code>{idPreview || "generated-from-label"}</code>
        </div>
        <button type="submit" disabled={adding || !idPreview || !displayGroup}>{adding ? "Adding…" : "Add topic tag"}</button>
      </div>
    </form>
  );
}

export function canonicalTopicId(label: string): string {
  const slug = label.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 64).replace(/-+$/g, "");
  return slug.length >= 2 ? slug : "";
}
