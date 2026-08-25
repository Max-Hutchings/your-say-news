import { useState } from "react";
import type { AutoPostCandidate, AutoPostError, AutoPostRun } from "../types";
import "./auto-post-desk.css";

interface AutoPostDeskProps {
  runs: AutoPostRun[] | null;
  activeRun: AutoPostRun | null;
  error: AutoPostError | null;
  creating: boolean;
  selectingCandidateId: string | null;
  approving?: boolean;
  retryingRunId?: string | null;
  onCreate: () => Promise<void>;
  onSelect: (runId: string, candidateId: string) => Promise<void>;
  onApprove?: (runId: string) => Promise<void>;
  onRetry?: (runId: string) => Promise<void>;
  onReload: () => Promise<void>;
}

export function AutoPostDesk({
  runs,
  activeRun,
  error,
  creating,
  selectingCandidateId,
  approving = false,
  retryingRunId = null,
  onCreate,
  onSelect,
  onApprove = async () => undefined,
  onRetry = async () => undefined,
  onReload,
}: AutoPostDeskProps) {
  const [expanded, setExpanded] = useState(new Set<string>());
  const [confirming, setConfirming] = useState<AutoPostCandidate | null>(null);
  const [confirmingApproval, setConfirmingApproval] = useState(false);

  const toggle = (candidateId: string) => setExpanded((current) => {
    const next = new Set(current);
    if (next.has(candidateId)) next.delete(candidateId);
    else next.add(candidateId);
    return next;
  });

  const confirm = async () => {
    if (!activeRun || !confirming) return;
    await onSelect(activeRun.id, confirming.id);
    setConfirming(null);
  };

  const confirmApproval = async () => {
    if (!activeRun) return;
    await onApprove(activeRun.id);
    setConfirmingApproval(false);
  };

  return (
    <main className="auto-post-desk">
      <header className="auto-post-intro">
        <div>
          <p className="auto-post-eyebrow">Official publishing</p>
          <h1>Your Say official posts</h1>
        </div>
        <div className="auto-post-intro__action">
          <p>Find the ten most important UK, US and global stories reported in the previous 24 hours.</p>
          <button type="button" disabled={creating} onClick={() => void onCreate()}>
            {creating ? "Finding stories…" : "Create new"}
          </button>
        </div>
      </header>

      {error ? <div className="auto-post-error" role="alert">
        <span>{error.message}</span>
        <button type="button" onClick={() => void onReload()}>Reload</button>
      </div> : null}

      {activeRun ? <ActiveRun
        run={activeRun}
        expanded={expanded}
        selectingCandidateId={selectingCandidateId}
        onToggle={toggle}
        onSelect={setConfirming}
        approving={approving}
        onApprove={() => setConfirmingApproval(true)}
      /> : null}

      <section className="auto-post-history" aria-labelledby="auto-post-history-title">
        <div className="auto-post-history__heading">
          <p>Publication ledger</p>
          <h2 id="auto-post-history-title">Previous official posts</h2>
        </div>
        {runs === null ? <p className="auto-post-empty" aria-live="polite">Reading the publication ledger…</p>
          : runs.length === 0 ? <p className="auto-post-empty">
            No official posts have been created through this desk yet.
          </p> : <ol>
            {runs.map((run) => <HistoryRow
              key={run.id}
              run={run}
              retrying={retryingRunId === run.id}
              retryDisabled={retryingRunId !== null}
              onRetry={onRetry}
            />)}
          </ol>}
      </section>

      {confirming ? <div className="auto-post-modal" role="presentation">
        <section role="dialog" aria-modal="true" aria-labelledby="confirm-story-title">
          <p className={`region-tag region-tag--${confirming.region.toLowerCase()}`}>
            {confirming.region === "GLOBAL" ? "Global" : confirming.region}
          </p>
          <h2 id="confirm-story-title">Confirm story selection</h2>
          <p className="auto-post-modal__headline">{confirming.headline}</p>
          <p>Post agent will research this story and create the article, voting question and options.</p>
          <div>
            <button type="button" className="secondary" onClick={() => setConfirming(null)}>Cancel</button>
            <button type="button" disabled={selectingCandidateId === confirming.id}
              onClick={() => void confirm()}>
              {selectingCandidateId === confirming.id ? "Starting draft…" : "Confirm and create draft"}
            </button>
          </div>
        </section>
      </div> : null}

      {confirmingApproval && activeRun?.draft ? <div className="auto-post-modal" role="presentation">
        <section role="dialog" aria-modal="true" aria-labelledby="confirm-publication-title">
          <p className="auto-post-eyebrow">Final editorial check</p>
          <h2 id="confirm-publication-title">Approve and publish?</h2>
          <p className="auto-post-modal__headline">
            {activeRun.candidates.find((candidate) => candidate.id === activeRun.selectedCandidateId)?.headline}
          </p>
          <p>This publishes the reviewed draft immediately as Your Say News.</p>
          <div>
            <button type="button" className="secondary" onClick={() => setConfirmingApproval(false)}>Cancel</button>
            <button type="button" disabled={approving} onClick={() => void confirmApproval()}>
              {approving ? "Publishing…" : "Approve and publish"}
            </button>
          </div>
        </section>
      </div> : null}
    </main>
  );
}

function ActiveRun({ run, expanded, selectingCandidateId, approving, onToggle, onSelect, onApprove }: {
  run: AutoPostRun;
  expanded: Set<string>;
  selectingCandidateId: string | null;
  approving: boolean;
  onToggle: (candidateId: string) => void;
  onSelect: (candidate: AutoPostCandidate) => void;
  onApprove: () => void;
}) {
  const progress = run.status === "QUEUED" || run.status === "DISCOVERING";
  return <section className="auto-post-run" aria-label="Current official-post workflow">
    <div className="news-window" aria-label="24-hour discovery window">
      <div className="news-window__times">
        <time dateTime={run.windowStart}>{formatWindow(run.windowStart)}</time>
        <span>Previous 24 hours</span>
        <time dateTime={run.windowEnd}>{formatWindow(run.windowEnd)}</time>
      </div>
      <div className="news-window__track"><span /></div>
    </div>

    {progress ? <div className="auto-post-progress" role="status">
      <span aria-hidden="true" />
      <div><strong>{run.status === "QUEUED" ? "Discovery queued" : "Reading today’s news"}</strong>
        <p>Progress is streaming from the auto-post service.</p></div>
    </div> : run.status === "FAILED" ? <div className="auto-post-error" role="alert">
      {run.errorMessage ?? "Story discovery failed."}
    </div> : run.status === "DRAFTING" ? <div className="auto-post-progress" role="status">
      <span aria-hidden="true" />
      <div><strong>Post agent is creating the draft</strong>
        <p>The selected story and verified discovery sources have been handed over.</p></div>
    </div> : run.status === "PUBLISHING" ? <div className="auto-post-progress" role="status">
      <span aria-hidden="true" />
      <div><strong>Publishing the approved post</strong>
        <p>Your Say News authorship and Pepper source provenance are being recorded.</p></div>
    </div> : null}

    {run.draft ? <DraftReview run={run} approving={approving} onApprove={onApprove} /> : null}

    {run.candidates.length > 0 ? <div className="candidate-section">
      <div className="candidate-section__heading">
        <p>Ranked across all regions</p><h2>Today’s ten stories</h2>
      </div>
      <ol className="candidate-ledger" aria-label="Top stories from the previous 24 hours">
        {run.candidates.map((candidate) => {
          const isExpanded = expanded.has(candidate.id);
          const locked = run.status !== "CANDIDATES_READY";
          return <li key={candidate.id}>
            <span className="candidate-rank">{candidate.rank}</span>
            <article>
              <div className="candidate-line">
                <button type="button" className="candidate-headline"
                  aria-expanded={isExpanded} onClick={() => onToggle(candidate.id)}>
                  {candidate.headline}
                </button>
                <span className={`region-tag region-tag--${candidate.region.toLowerCase()}`}>
                  {candidate.region === "GLOBAL" ? "Global" : candidate.region}
                </span>
                <button type="button" className="candidate-select" disabled={locked || selectingCandidateId !== null}
                  onClick={() => onSelect(candidate)} aria-label={`Select ${candidate.headline}`}>
                  Select
                </button>
              </div>
              {isExpanded ? <div className="candidate-summary">
                <p>{candidate.summary}</p>
                <ul>{candidate.sources.map((source) => <li key={source.url}>
                  <a href={source.url} target="_blank" rel="noreferrer">{source.publisher}</a>
                </li>)}</ul>
              </div> : null}
            </article>
          </li>;
        })}
      </ol>
    </div> : null}
  </section>;
}

function DraftReview({ run, approving, onApprove }: {
  run: AutoPostRun;
  approving: boolean;
  onApprove: () => void;
}) {
  if (!run.draft) return null;
  const selected = run.candidates.find((candidate) => candidate.id === run.selectedCandidateId);
  return <section className="auto-post-draft" aria-labelledby="auto-post-draft-title">
    <div className="auto-post-draft__heading">
      <div><p>Post agent draft</p><h2 id="auto-post-draft-title">Review before publishing</h2></div>
      <button type="button" disabled={run.status !== "DRAFT_READY" || approving} onClick={onApprove}>
        {approving ? "Publishing…" : "Approve draft"}
      </button>
    </div>
    {selected ? <h3>{selected.headline}</h3> : null}
    <p>{run.draft.summary}</p>
    {run.draft.caseFor ? <div><strong>Case for</strong><p>{run.draft.caseFor}</p></div> : null}
    {run.draft.caseAgainst ? <div><strong>Case against</strong><p>{run.draft.caseAgainst}</p></div> : null}
    <div className="auto-post-draft__vote">
      <strong>{run.draft.supportQuestion}</strong>
      <ul>{run.draft.voteOptions.map((option) => <li key={option}>{option}</li>)}</ul>
    </div>
    <div className="auto-post-draft__sources">
      <strong>Sources</strong>
      <ul>{run.draft.citations.map((source) => <li key={source.url}>
        <a href={source.url} target="_blank" rel="noreferrer">{source.publisher}: {source.title}</a>
      </li>)}</ul>
    </div>
  </section>;
}

function HistoryRow({ run, retrying, retryDisabled, onRetry }: {
  run: AutoPostRun;
  retrying: boolean;
  retryDisabled: boolean;
  onRetry: (runId: string) => Promise<void>;
}) {
  const selected = run.candidates.find((candidate) => candidate.id === run.selectedCandidateId);
  const canRetryDraft = run.status === "FAILED"
    && run.selectedCandidateId !== null
    && run.pepperDraftId !== null;
  return <li>
    <time dateTime={run.createdAt}>{formatHistoryDate(run.createdAt)}</time>
    <div><strong>{selected?.headline ?? statusLabel(run.status)}</strong>
      <span>{statusLabel(run.status)}</span></div>
    {canRetryDraft ? <button
      type="button"
      className="auto-post-history__retry"
      disabled={retryDisabled}
      onClick={() => void onRetry(run.id)}
    >{retrying ? "Retrying…" : "Retry draft"}</button>
      : <span>{run.publishedPostId ? `Post ${run.publishedPostId}` : "-"}</span>}
  </li>;
}

function statusLabel(status: AutoPostRun["status"]) {
  return ({
    QUEUED: "Queued",
    DISCOVERING: "Finding stories",
    CANDIDATES_READY: "Awaiting selection",
    DRAFTING: "Drafting",
    DRAFT_READY: "Awaiting approval",
    PUBLISHING: "Publishing",
    FAILED: "Failed",
    PUBLISHED: "Published",
  } as const)[status];
}

const windowFormatter = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
  hour12: false, timeZone: "Europe/London",
});
const historyFormatter = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit", month: "short", year: "numeric", timeZone: "Europe/London",
});
function formatWindow(value: string) { return windowFormatter.format(new Date(value)); }
function formatHistoryDate(value: string) { return historyFormatter.format(new Date(value)); }
