import { useMemo, useState } from "react";
import type {
  UnwrappedAdminPost,
  UnwrappedGenerationMonitor,
  UnwrappedGenerationState,
  UnwrappedGenerationStatus,
  UnwrappedGenerationTrigger,
  UnwrappedReviewError,
} from "../types";

type EligibilityFilter = "ALL" | "ELIGIBLE" | "BUILDING";

type UnwrappedAnalysisPostsProps = {
  posts: UnwrappedAdminPost[] | null;
  error: UnwrappedReviewError | null;
  generatingPostId: number | null;
  generationError: UnwrappedReviewError | null;
  generationMonitor: UnwrappedGenerationMonitor | null;
  onReload: () => Promise<void>;
  onGenerate: (postId: number) => Promise<UnwrappedGenerationTrigger>;
};

const FIRST_MILESTONE = 100;

export function UnwrappedAnalysisPosts({
  posts,
  error,
  generatingPostId,
  generationError,
  generationMonitor,
  onReload,
  onGenerate,
}: UnwrappedAnalysisPostsProps) {
  const [query, setQuery] = useState("");
  const [eligibility, setEligibility] = useState<EligibilityFilter>("ALL");

  const filteredPosts = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (posts ?? []).filter((post) => {
      const searchable = [
        post.postId,
        post.question,
        post.summary,
        post.jurisdiction,
        ...post.overall.map((option) => option.label),
      ].join(" ").toLowerCase();
      const isEligible = post.canonicalVoteCount >= FIRST_MILESTONE;
      const matchesEligibility = eligibility === "ALL"
        || (eligibility === "ELIGIBLE" ? isEligible : !isEligible);
      return (!normalizedQuery || searchable.includes(normalizedQuery)) && matchesEligibility;
    });
  }, [eligibility, posts, query]);

  const totals = useMemo(() => ({
    posts: posts?.length ?? 0,
    eligible: posts?.filter((post) => post.canonicalVoteCount >= FIRST_MILESTONE).length ?? 0,
    votes: posts?.reduce((sum, post) => sum + post.canonicalVoteCount, 0) ?? 0,
  }), [posts]);

  const statusByPost = useMemo(() => new Map(
    generationMonitor?.statuses.map((status) => [status.postId, status]) ?? [],
  ), [generationMonitor]);

  const activeTotals = useMemo(() => ({
    queued: generationMonitor?.statuses.filter((status) => status.state === "QUEUED").length ?? 0,
    generating: generationMonitor?.statuses.filter((status) => status.state === "GENERATING").length ?? 0,
    failed: generationMonitor?.statuses.filter((status) => status.state === "FAILED").length ?? 0,
  }), [generationMonitor]);

  const generate = async (postId: number) => {
    try {
      await onGenerate(postId);
    } catch {
      // The request error is rendered above the ledger with retry guidance.
    }
  };

  return (
    <section className="analysis-posts" aria-labelledby="analysis-posts-title">
      <header className="analysis-posts__header">
        <div>
          <p>Analysis runs</p>
          <h2 id="analysis-posts-title">Choose a post to unwrap</h2>
        </div>
        <p>
          Run the normal milestone check without adding a vote. Posts need 100 votes before a new
          analysis job can enter the review queue.
        </p>
      </header>

      <dl className="analysis-totals" aria-label="Post analysis totals">
        <div><dt>Recent posts</dt><dd>{totals.posts}</dd></div>
        <div><dt>Ready at 100+</dt><dd>{totals.eligible}</dd></div>
        <div><dt>Votes shown</dt><dd>{totals.votes.toLocaleString("en-GB")}</dd></div>
      </dl>

      <div
        className={generationMonitor?.workerAvailable === false
          ? "generation-monitor generation-monitor--paused"
          : "generation-monitor"}
        aria-live="polite"
      >
        <span className="generation-monitor__signal" aria-hidden="true" />
        <div>
          <strong>
            {generationMonitor === null
              ? "Checking generation worker…"
              : generationMonitor.workerAvailable
                ? "Generation worker online"
                : "Generation paused — API key unavailable"}
          </strong>
          <p>
            {generationMonitor?.workerAvailable === false
              ? "Queued work will not start until post-service is restarted with UNWRAPPED_API_KEY, YOUR_SAY_NEWS_GROK_API_KEY or XAI_API_KEY."
              : "This page checks progress automatically every four seconds."}
          </p>
        </div>
        <dl aria-label="Generation progress totals">
          <div><dt>Generating</dt><dd>{activeTotals.generating}</dd></div>
          <div><dt>Queued</dt><dd>{activeTotals.queued}</dd></div>
          <div><dt>Failed</dt><dd>{activeTotals.failed}</dd></div>
        </dl>
      </div>

      <div className="analysis-tools" aria-label="Post filters">
        <label className="analysis-search">
          <span>Find a post</span>
          <input
            type="search"
            value={query}
            placeholder="Question, summary or post ID"
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <label>
          <span>Milestone</span>
          <select
            value={eligibility}
            onChange={(event) => setEligibility(event.target.value as EligibilityFilter)}
          >
            <option value="ALL">Any vote total</option>
            <option value="ELIGIBLE">Ready to analyse</option>
            <option value="BUILDING">Below 100 votes</option>
          </select>
        </label>
        <button type="button" className="analysis-refresh" onClick={() => void onReload()}>
          Refresh posts
        </button>
      </div>

      {error ? (
        <div className="analysis-error" role="alert">
          <span>{error.message}</span>
          <button type="button" onClick={() => void onReload()}>Reload posts</button>
        </div>
      ) : null}

      {generationError ? (
        <div className="analysis-error" role="alert">
          <span>{generationError.message}</span>
        </div>
      ) : null}

      {posts === null ? (
        <div className="analysis-loading" aria-live="polite">Reading post vote totals…</div>
      ) : filteredPosts.length === 0 ? (
        <div className="analysis-empty">
          <p>No posts match these filters.</p>
          <span>Clear the search or choose another milestone state.</span>
        </div>
      ) : (
        <div className="analysis-ledger">
          <div className="analysis-ledger__heading" aria-hidden="true">
            <span>Post details</span>
            <span>Overall vote split</span>
            <span>Total</span>
            <span>Analysis</span>
          </div>
          <ol>
            {filteredPosts.map((post) => {
              const ready = post.canonicalVoteCount >= FIRST_MILESTONE;
              const status = statusByPost.get(post.postId);
              const state = status?.state ?? "NOT_STARTED";
              const busy = state === "QUEUED" || state === "GENERATING";
              const action = generationAction(
                state,
                ready,
                post.canonicalVoteCount,
                generationMonitor?.workerAvailable,
                status,
              );
              return (
                <li key={post.postId}>
                  <article className={ready ? "analysis-row analysis-row--ready" : "analysis-row"}>
                    <div className="analysis-row__story">
                      <p className="analysis-row__meta">
                        Post {post.postId} · {formatDate(post.createdAt)} · {formatEnum(post.jurisdiction)}
                      </p>
                      <h3>{post.question}</h3>
                      <p className="analysis-row__summary">{post.summary}</p>
                      {(post.caseFor || post.caseAgainst) ? (
                        <details className="analysis-row__details">
                          <summary>Post details</summary>
                          <div>
                            {post.caseFor ? <p><strong>Case for</strong>{post.caseFor}</p> : null}
                            {post.caseAgainst ? <p><strong>Case against</strong>{post.caseAgainst}</p> : null}
                          </div>
                        </details>
                      ) : null}
                    </div>

                    <div className="vote-split" aria-label={splitLabel(post)}>
                      <div className="vote-split__bar" aria-hidden="true">
                        {post.overall.map((option) => (
                          <span
                            key={option.optionId}
                            data-semantic={option.semanticKey}
                            style={{ width: `${option.percentage}%` }}
                          />
                        ))}
                      </div>
                      <ol>
                        {post.overall.map((option) => (
                          <li key={option.optionId} data-semantic={option.semanticKey}>
                            <span>{option.label}</span>
                            <strong>{formatPercentage(option.percentage)}</strong>
                            <small>{option.count.toLocaleString("en-GB")} votes</small>
                          </li>
                        ))}
                      </ol>
                    </div>

                    <div className="analysis-row__total">
                      <strong>{post.canonicalVoteCount.toLocaleString("en-GB")}</strong>
                      <span>votes</span>
                    </div>

                    <div className={`analysis-row__action analysis-row__action--${state.toLowerCase()}`}>
                      <span className={action.emphasis ? "analysis-readiness analysis-readiness--ready" : "analysis-readiness"}>
                        {action.label}
                      </span>
                      <small>{action.detail}</small>
                      <button
                        type="button"
                        disabled={generatingPostId !== null || action.disabled}
                        onClick={() => void generate(post.postId)}
                        aria-label={`Run analysis for post ${post.postId}`}
                      >
                        {generatingPostId === post.postId ? "Queuing…" : action.button}
                      </button>
                      {busy ? <span className="analysis-progress-pulse" role="status">Progress updates automatically</span> : null}
                    </div>
                  </article>
                </li>
              );
            })}
          </ol>
        </div>
      )}
    </section>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(value));
}

function formatEnum(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
}

function formatPercentage(value: number) {
  return `${value.toLocaleString("en-GB", { maximumFractionDigits: 1 })}%`;
}

function splitLabel(post: UnwrappedAdminPost) {
  return post.overall
    .map((option) => `${option.label}: ${formatPercentage(option.percentage)}, ${option.count} votes`)
    .join("; ");
}

function votesToMilestone(voteCount: number) {
  const remaining = FIRST_MILESTONE - voteCount;
  return `${remaining} ${remaining === 1 ? "vote" : "votes"} to go`;
}

function generationAction(
  state: UnwrappedGenerationState,
  eligible: boolean,
  voteCount: number,
  workerAvailable: boolean | undefined,
  status: UnwrappedGenerationStatus | undefined,
) {
  if (!eligible) {
    return {
      label: votesToMilestone(voteCount),
      detail: "Analysis unlocks at 100 votes.",
      button: "Not eligible",
      disabled: true,
      emphasis: false,
    };
  }
  if (state === "QUEUED") return {
    label: "Queued for analysis",
    detail: "Waiting for a generation worker to pick this up.",
    button: "Queued",
    disabled: true,
    emphasis: true,
  };
  if (state === "GENERATING") return {
    label: "Generating now",
    detail: "Researching sources and writing the draft. This normally takes a few minutes.",
    button: "Generating…",
    disabled: true,
    emphasis: true,
  };
  if (state === "READY_FOR_REVIEW") return {
    label: "Draft ready for review",
    detail: "The generated draft is in the review queue below.",
    button: "Generated",
    disabled: true,
    emphasis: true,
  };
  if (state === "FAILED") return {
    label: "Generation failed",
    detail: status?.errorMessage ?? "Check the post-service logs for the failure details.",
    button: "Failed",
    disabled: true,
    emphasis: false,
  };
  if (workerAvailable === false) return {
    label: "Worker unavailable",
    detail: "Configure the API key and restart post-service before starting analysis.",
    button: "Unavailable",
    disabled: true,
    emphasis: false,
  };
  return {
    label: "Ready to analyse",
    detail: "Starts the milestone check and generation queue.",
    button: "Run analysis",
    disabled: false,
    emphasis: true,
  };
}
