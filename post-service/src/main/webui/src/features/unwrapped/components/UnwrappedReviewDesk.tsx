import { useEffect, useMemo, useState } from "react";
import type {
  UnwrappedAdminPost,
  UnwrappedGenerationTrigger,
  UnwrappedGenerationMonitor,
  UnwrappedReviewError,
  UnwrappedReviewStory,
} from "../types";
import { UnwrappedAnalysisPosts } from "./UnwrappedAnalysisPosts";
import { UnwrappedMarkdown } from "./UnwrappedMarkdown";
import "./unwrapped-review-desk.css";

type UnwrappedReviewDeskProps = {
  reviews: UnwrappedReviewStory[] | null;
  posts: UnwrappedAdminPost[] | null;
  postsError: UnwrappedReviewError | null;
  error: UnwrappedReviewError | null;
  actingStoryId: string | null;
  generatingPostId: number | null;
  generationError: UnwrappedReviewError | null;
  generationMonitor: UnwrappedGenerationMonitor | null;
  onReload: () => Promise<void>;
  onReloadPosts: () => Promise<void>;
  onApprove: (storyId: string) => Promise<UnwrappedReviewStory>;
  onReject: (storyId: string, reason: string) => Promise<UnwrappedReviewStory>;
  onGenerate: (postId: number) => Promise<UnwrappedGenerationTrigger>;
  onBenchmark: (post: UnwrappedAdminPost) => void;
};

export function UnwrappedReviewDesk({
  reviews,
  posts,
  postsError,
  error,
  actingStoryId,
  generatingPostId,
  generationError,
  generationMonitor,
  onReload,
  onReloadPosts,
  onApprove,
  onReject,
  onGenerate,
  onBenchmark,
}: UnwrappedReviewDeskProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");

  useEffect(() => {
    if (reviews === null) {
      return;
    }
    if (!reviews.some((story) => story.storyId === selectedId)) {
      setSelectedId(reviews[0]?.storyId ?? null);
      setRejecting(false);
      setReason("");
    }
  }, [reviews, selectedId]);

  const selected = useMemo(
    () => reviews?.find((story) => story.storyId === selectedId) ?? null,
    [reviews, selectedId],
  );

  const approve = async () => {
    if (!selected) return;
    try {
      await onApprove(selected.storyId);
    } catch {
      // The inline error retains the selected draft and gives retry guidance.
    }
  };

  const reject = async () => {
    if (!selected || !reason.trim()) return;
    try {
      await onReject(selected.storyId, reason.trim());
      setRejecting(false);
      setReason("");
    } catch {
      // Keep the reason so the reviewer can retry without retyping it.
    }
  };

  return (
    <main className="unwrapped-page">
      <header className="unwrapped-page__intro">
        <div>
          <p className="unwrapped-page__eyebrow">Publication control</p>
          <h1>Unwrapped desk</h1>
        </div>
        <p className="unwrapped-page__standfirst">
          Read every argument as a voter will see it, then approve it for publication or return it.
        </p>
      </header>

      <UnwrappedAnalysisPosts
        posts={posts}
        error={postsError}
        generatingPostId={generatingPostId}
        generationError={generationError}
        generationMonitor={generationMonitor}
        onReload={onReloadPosts}
        onGenerate={onGenerate}
        onBenchmark={onBenchmark}
      />

      <div className="unwrapped-status-line">
        <span>Awaiting decision</span>
        <div>
          <button type="button" onClick={() => void onReload()}>Refresh queue</button>
          <strong>{reviews?.length ?? "—"}</strong>
        </div>
      </div>

      {error ? (
        <div className="review-error" role="alert">
          <span>{error.message}</span>
          <button type="button" onClick={() => void onReload()}>Reload reviews</button>
        </div>
      ) : null}

      {reviews === null ? (
        <div className="review-loading" aria-live="polite">Opening the review queue…</div>
      ) : reviews.length === 0 ? (
        <div className="review-empty">
          <p>The publication desk is clear.</p>
          <span>New Unwrapped drafts will appear here when they are ready for review.</span>
        </div>
      ) : (
        <div className="review-workspace">
          <aside className="review-queue" aria-label="Unwrapped drafts awaiting review">
            <p className="review-queue__label">Queue</p>
            <ol>
              {reviews.map((story, index) => (
                <li key={story.storyId}>
                  <button
                    type="button"
                    className={story.storyId === selectedId ? "review-ticket review-ticket--active" : "review-ticket"}
                    aria-pressed={story.storyId === selectedId}
                    onClick={() => {
                      setSelectedId(story.storyId);
                      setRejecting(false);
                      setReason("");
                    }}
                  >
                    <span className="review-ticket__number">{String(index + 1).padStart(2, "0")}</span>
                    <span>
                      <strong>Post {story.postId}</strong>
                      <small>{story.canonicalVoteCount} votes · milestone {story.milestone}</small>
                    </span>
                    <time dateTime={story.generatedAt}>
                      {formatDate(story.generatedAt)}
                    </time>
                  </button>
                </li>
              ))}
            </ol>
          </aside>

          {selected ? (
            <article className="review-proof" aria-label={`Review Unwrapped story for post ${selected.postId}`}>
              <header className="review-proof__header">
                <div>
                  <p>Post {selected.postId} · {selected.canonicalVoteCount} canonical votes</p>
                  <h2>Publication proof</h2>
                </div>
                <span>{selected.argumentPages.length} arguments</span>
              </header>

              <div className="review-proof__pages">
                {selected.argumentPages.map((page) => {
                  const option = selected.options.find((value) => value.id === page.optionId);
                  const sourceIds = page.sources.map((source) => source.id);
                  return (
                  <section key={page.optionId} className="review-argument">
                    <p className="review-argument__kicker">
                      THE CASE FOR
                    </p>
                    <p className="review-argument__option">
                      {option?.label ?? `Option ${page.optionId}`}
                    </p>
                    <h3>{page.headline}</h3>
                    <div className="review-argument__article">
                      {page.paragraphs.map((paragraph, paragraphIndex) => (
                        <div key={`${page.optionId}-${paragraphIndex}`}
                          className="review-argument__paragraph">
                          <UnwrappedMarkdown text={paragraph.text} />
                          <small>{paragraph.sourceIds.map(
                            (id) => `[${sourceIds.indexOf(id) + 1}]`,
                          ).join(" ")}</small>
                        </div>
                      ))}
                    </div>
                    <div className="review-argument__sources">
                      <p>Data sources</p>
                      <ol>
                        {page.sources.map((source, sourceIndex) => (
                          <li key={source.id}>
                            <span>{String(sourceIndex + 1).padStart(2, "0")}</span>
                            <div>
                              <a href={source.url} target="_blank" rel="noreferrer">{source.title}</a>
                              <small>{source.publisher} · {source.classification.replace("_", " ")}</small>
                            </div>
                          </li>
                        ))}
                      </ol>
                    </div>
                  </section>
                  );
                })}
              </div>

              {rejecting ? (
                <section className="review-rejection" aria-label="Reject draft">
                  <label>
                    <span>Reason for returning this draft</span>
                    <textarea
                      value={reason}
                      maxLength={512}
                      autoFocus
                      placeholder="Describe what must change before publication."
                      onChange={(event) => setReason(event.target.value)}
                    />
                  </label>
                  <div>
                    <button type="button" className="review-button review-button--quiet"
                      onClick={() => setRejecting(false)}>
                      Cancel
                    </button>
                    <button type="button" className="review-button review-button--reject"
                      disabled={!reason.trim() || actingStoryId === selected.storyId}
                      onClick={() => void reject()}>
                      {actingStoryId === selected.storyId ? "Returning…" : "Return draft"}
                    </button>
                  </div>
                </section>
              ) : (
                <footer className="review-actions">
                  <button type="button" className="review-button review-button--reject"
                    disabled={actingStoryId === selected.storyId}
                    onClick={() => setRejecting(true)}>
                    Return for changes
                  </button>
                  <button type="button" className="review-button review-button--approve"
                    disabled={actingStoryId === selected.storyId}
                    onClick={() => void approve()}>
                    {actingStoryId === selected.storyId ? "Publishing…" : "Approve publication"}
                  </button>
                </footer>
              )}
            </article>
          ) : null}
        </div>
      )}
    </main>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
  }).format(new Date(value));
}
