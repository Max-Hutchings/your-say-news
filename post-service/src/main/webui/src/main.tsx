import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import "./shared/styles/fonts.css";
import "./shared/styles/editorial.css";
import "./shared/styles/global.css";

const root = document.getElementById("root");

if (!root) {
  throw new Error("The admin application root element was not found.");
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
