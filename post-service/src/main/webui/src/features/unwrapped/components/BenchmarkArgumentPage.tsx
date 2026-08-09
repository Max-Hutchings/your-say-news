import type { UnwrappedArgument } from "../types";

type BenchmarkArgumentPageProps = {
  page: UnwrappedArgument;
  optionLabel: string;
};

export function BenchmarkArgumentPage({ page, optionLabel }: BenchmarkArgumentPageProps) {
  const sourceIds = page.sources.map((source) => source.id);

  return (
    <section className="benchmark-argument">
      <p className="benchmark-argument__kicker">The case for</p>
      <p className="benchmark-argument__option">{optionLabel}</p>
      <h3>{page.headline}</h3>
      <div className="benchmark-argument__copy">
        {page.paragraphs.map((paragraph, paragraphIndex) => (
          <div key={`${page.optionId}-${paragraphIndex}`}>
            <p>{paragraph.text}</p>
            <small>
              {paragraph.sourceIds.map((id) => `[${sourceIds.indexOf(id) + 1}]`).join(" ")}
            </small>
          </div>
        ))}
      </div>
      <div className="benchmark-argument__sources">
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
}
