import { Masthead } from "../../shared/components/Masthead";
import "./home-page.css";

export function HomePage() {
  return (
    <div className="admin-shell">
      <Masthead />

      <main className="hello-page">
        <p className="hello-page__eyebrow">Admin web console · foundation</p>

        <div className="hello-page__message">
          <h1>
            Hello from{" "}
            <span>Your Say News.</span>
          </h1>
          <div className="hello-page__signal" aria-hidden="true">
            <span>Y</span>
          </div>
        </div>

        <div className="hello-page__footer">
          <p>The administration workspace is ready for its first capability.</p>
          <p className="hello-page__status">
            <span aria-hidden="true" />
            React + Quarkus
          </p>
        </div>
      </main>
    </div>
  );
}
