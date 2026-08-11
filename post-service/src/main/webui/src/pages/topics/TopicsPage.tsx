import { AddTopicForm, TopicLedger, useAdminTopics } from "../../features/topics";
import "./topics-page.css";

export function TopicsPage() {
  const { topics, error, savingIds, adding, load, add, setActive } = useAdminTopics();
  const activeCount = topics?.filter((topic) => topic.active).length ?? 0;

  return (
    <main className="topics-page">
      <header className="topics-page__intro">
        <div><p>Discovery &amp; filing</p><h1>Topic tags desk</h1></div>
        <p>Keep story categories useful, distinct and easy for readers to scan.</p>
      </header>

      <dl className="topic-totals" aria-label="Topic tag totals">
        <div><dt>Catalogue</dt><dd>{topics?.length ?? 0}</dd></div>
        <div><dt>Active</dt><dd>{activeCount}</dd></div>
        <div><dt>Retired</dt><dd>{(topics?.length ?? 0) - activeCount}</dd></div>
      </dl>

      {topics ? <AddTopicForm topics={topics} adding={adding} onAdd={add} /> : null}

      {error ? <div className="topic-error" role="alert"><span>{error.message}</span><button type="button" onClick={() => void load()}>Reload topic tags</button></div> : null}
      {topics === null ? <div className="topic-loading" aria-live="polite">Reading the topic tag catalogue…</div> : <TopicLedger topics={topics} savingIds={savingIds} onSetActive={setActive} />}
    </main>
  );
}
