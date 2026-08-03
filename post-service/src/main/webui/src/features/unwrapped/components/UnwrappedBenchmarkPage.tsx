import { useEffect, useState } from "react";
import {
  generateUnwrappedBenchmark,
  getUnwrappedBenchmarkPrompt,
  UnwrappedAdminApiError,
} from "../services/unwrappedAdminApi";
import type {
  UnwrappedAdminPost,
  UnwrappedBenchmarkVariant,
  UnwrappedReviewError,
} from "../types";
import { BenchmarkArgumentPage } from "./BenchmarkArgumentPage";
import "./unwrapped-benchmark-page.css";

type UnwrappedBenchmarkPageProps = {
  post: UnwrappedAdminPost;
  onBack: () => void;
};

const LANE_NAMES = ["A", "B", "C"];

export function UnwrappedBenchmarkPage({ post, onBack }: UnwrappedBenchmarkPageProps) {
  const [prompts, setPrompts] = useState(["", "", ""]);
  const [loadingPrompt, setLoadingPrompt] = useState(true);
  const [running, setRunning] = useState([false, false, false]);
  const [error, setError] = useState<UnwrappedReviewError | null>(null);
  const [laneErrors, setLaneErrors] = useState<(string | null)[]>([null, null, null]);
  const [variants, setVariants] = useState<(UnwrappedBenchmarkVariant | null)[]>(
    [null, null, null],
  );
  const [resultOptions, setResultOptions] = useState<Array<{
    id: number;
    label: string;
    ordinal: number;
    semanticKey: string | null;
  }>>(
    post.overall.map((option) => ({
      id: option.optionId,
      label: option.label,
      ordinal: option.ordinal,
      semanticKey: option.semanticKey,
    })),
  );

  useEffect(() => {
    let active = true;
    void getUnwrappedBenchmarkPrompt()
      .then(({ systemPrompt }) => {
        if (active) setPrompts([systemPrompt, systemPrompt, systemPrompt]);
      })
      .catch((reason) => {
        if (active) setError(toBenchmarkError(reason));
      })
      .finally(() => {
        if (active) setLoadingPrompt(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const updatePrompt = (index: number, value: string) => {
    setPrompts((current) => current.map((prompt, promptIndex) => (
      promptIndex === index ? value : prompt
    )));
  };

  const generateLane = async (index: number) => {
    const systemPrompt = prompts[index];
    if (!systemPrompt.trim()) return;
    const previous = variants[index];
    setRunning((current) => replaceAt(current, index, true));
    setLaneErrors((current) => replaceAt(current, index, null));
    try {
      const benchmark = await generateUnwrappedBenchmark(post.postId, [systemPrompt]);
      const next = benchmark.variants[0];
      setResultOptions(benchmark.options);
      if (next.status === "FAILED" && previous?.status === "SUCCEEDED") {
        setLaneErrors((current) => replaceAt(
          current,
          index,
          next.errorMessage ?? "This prompt did not produce a valid Unwrapped draft.",
        ));
      } else {
        setVariants((current) => replaceAt(current, index, next));
      }
    } catch (reason) {
      setLaneErrors((current) => replaceAt(current, index, toBenchmarkError(reason).message));
    } finally {
      setRunning((current) => replaceAt(current, index, false));
    }
  };

  return (
    <main className="benchmark-page">
      <button type="button" className="benchmark-back" onClick={onBack}>
        <span aria-hidden="true">←</span> Back to Unwrapped desk
      </button>

      <header className="benchmark-dossier">
        <div className="benchmark-dossier__story">
          <p>Prompt benchmark · Post {post.postId}</p>
          <h1>{post.question}</h1>
          <p className="benchmark-dossier__summary">{post.summary}</p>
          <dl>
            <div><dt>Jurisdiction</dt><dd>{formatEnum(post.jurisdiction)}</dd></div>
            <div><dt>Voting format</dt><dd>{formatEnum(post.votingType)}</dd></div>
            <div><dt>Canonical votes</dt><dd>{post.canonicalVoteCount.toLocaleString("en-GB")}</dd></div>
          </dl>
        </div>

        <div className="benchmark-dossier__vote" aria-label={splitLabel(post)}>
          <p>Fixed comparison evidence</p>
          <div className="benchmark-vote-bar" aria-hidden="true">
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
          {(post.caseFor || post.caseAgainst) ? (
            <div className="benchmark-dossier__cases">
              {post.caseFor ? <p><strong>Case for</strong>{post.caseFor}</p> : null}
              {post.caseAgainst ? <p><strong>Case against</strong>{post.caseAgainst}</p> : null}
            </div>
          ) : null}
        </div>
      </header>

      <section>
        <header className="benchmark-instructions">
          <div>
            <p>Up to three controlled variants</p>
            <h2>Change the voice. Keep the evidence.</h2>
          </div>
          <p>
            Each editor replaces the complete system prompt. Generate and iterate on any lane
            without clearing the comparisons already produced in the others.
          </p>
        </header>

        {error ? <div className="benchmark-error" role="alert">{error.message}</div> : null}

        <div className="benchmark-rail">
          {prompts.map((prompt, index) => {
            const variant = variants[index];
            const laneRunning = running[index];
            const promptChanged = variant !== null && variant.systemPrompt !== prompt;
            return (
              <article
                key={LANE_NAMES[index]}
                className={`benchmark-lane benchmark-lane--${index + 1}`}
              >
                <label htmlFor={`benchmark-prompt-${index}`}>
                  <span>Prompt {LANE_NAMES[index]}</span>
                  <small>{prompt.length.toLocaleString("en-GB")} characters</small>
                </label>
                <textarea
                  id={`benchmark-prompt-${index}`}
                  value={prompt}
                  maxLength={20_000}
                  disabled={loadingPrompt || laneRunning}
                  placeholder={loadingPrompt ? "Loading the production prompt…" : "Paste a complete system prompt"}
                  onChange={(event) => updatePrompt(index, event.target.value)}
                />

                <div className="benchmark-lane__controls">
                  <small>
                    {variant ? `A successful rerun replaces result ${LANE_NAMES[index]} only.`
                      : `Creates result ${LANE_NAMES[index]} only.`}
                  </small>
                  <button
                    type="button"
                    disabled={loadingPrompt || laneRunning || !prompt.trim()}
                    onClick={() => void generateLane(index)}
                  >
                    {laneRunning
                      ? `Generating prompt ${LANE_NAMES[index]}…`
                      : `Generate prompt ${LANE_NAMES[index]}`}
                  </button>
                </div>

                <div className="benchmark-lane__handoff" aria-hidden="true">
                  <span>{LANE_NAMES[index]}</span>
                </div>

                <section className="benchmark-result" aria-label={`Result ${LANE_NAMES[index]}`}>
                  {laneErrors[index] ? (
                    <div className="benchmark-lane__error" role="alert">{laneErrors[index]}</div>
                  ) : null}
                  {laneRunning && variant ? (
                    <div className="benchmark-result__refreshing" role="status">
                      Generating a new result; the current comparison stays visible.
                    </div>
                  ) : null}
                  {!variant && laneRunning ? (
                    <div className="benchmark-result__waiting" aria-live="polite">
                      <span />
                      <strong>Generating comparison {LANE_NAMES[index]}…</strong>
                      <p>Researching, writing and checking every citation.</p>
                    </div>
                  ) : !variant ? (
                    <div className="benchmark-result__empty">
                      <strong>Result {LANE_NAMES[index]}</strong>
                      <p>The generated article will continue down this lane.</p>
                    </div>
                  ) : variant.status === "FAILED" ? (
                    <div className="benchmark-result__failed" role="alert">
                      <strong>Prompt {LANE_NAMES[index]} failed</strong>
                      <p>{variant.errorMessage}</p>
                      <code>{variant.errorCode}</code>
                      <BenchmarkAttemptDetails variant={variant} />
                    </div>
                  ) : (
                    <>
                      <header className="benchmark-result__meta">
                        <span>{promptChanged ? "Prompt edited · result preserved"
                          : `Completed · ${formatAttempts(variant.attemptCount)}`}</span>
                        <small>{variant.model}</small>
                      </header>
                      <BenchmarkAttemptDetails variant={variant} />
                      {variant.argumentPages.map((page) => (
                        <BenchmarkArgumentPage
                          key={page.optionId}
                          page={page}
                          optionLabel={resultOptions.find((option) => option.id === page.optionId)?.label
                            ?? `Option ${page.optionId}`}
                        />
                      ))}
                    </>
                  )}
                </section>
              </article>
            );
          })}
        </div>

        <div className="benchmark-runbar">
          <div>
            <strong>{variants.filter(Boolean).length} of 3 comparisons generated</strong>
            <span>Run lanes independently. Nothing from this page enters the publication queue.</span>
          </div>
        </div>
      </section>
    </main>
  );
}

function BenchmarkAttemptDetails({ variant }: { variant: UnwrappedBenchmarkVariant }) {
  if (variant.attemptCount <= 1 || variant.effectiveSystemPrompt === variant.systemPrompt) {
    return null;
  }
  return (
    <details className="benchmark-result__repair">
      <summary>{formatAttempts(variant.attemptCount)} · view effective repair prompt</summary>
      <pre>{variant.effectiveSystemPrompt}</pre>
    </details>
  );
}

function formatAttempts(attemptCount: number) {
  return `${attemptCount} ${attemptCount === 1 ? "attempt" : "attempts"}`;
}

function toBenchmarkError(reason: unknown): UnwrappedReviewError {
  if (reason instanceof UnwrappedAdminApiError) {
    return { status: reason.status, message: reason.message };
  }
  return {
    status: null,
    message: reason instanceof Error ? reason.message : "The benchmark could not be generated.",
  };
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

function replaceAt<T>(values: T[], index: number, value: T): T[] {
  return values.map((current, currentIndex) => currentIndex === index ? value : current);
}
