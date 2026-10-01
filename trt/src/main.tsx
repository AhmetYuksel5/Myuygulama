import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./tasarim.css";

const kok = document.getElementById("kok");
if (!kok) throw new Error("Kök eleman bulunamadı.");

createRoot(kok).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
