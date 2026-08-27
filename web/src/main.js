import { App } from "./view/App.js";
import "./styles.css";

try {
  new App(document.getElementById("app"));
} catch (err) {
  console.error(err);
  const status = document.getElementById("status-label");
  if (status) {
    status.textContent = String(err && err.message ? err.message : err);
  }
}
