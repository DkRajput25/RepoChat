import { Code2 } from "lucide-react";
import { SiC, SiCplusplus, SiCss, SiGo, SiHtml5, SiJavascript, SiJupyter, SiOpenjdk, SiPython, SiReact, SiRuby, SiRust, SiTypescript } from "react-icons/si";

const languages = {
  javascript: [SiJavascript, "language-js"], typescript: [SiTypescript, "language-ts"], python: [SiPython, "language-python"],
  java: [SiOpenjdk, "language-java"], html: [SiHtml5, "language-html"], css: [SiCss, "language-css"],
  "jupyter notebook": [SiJupyter, "language-notebook"], c: [SiC, "language-c"], "c++": [SiCplusplus, "language-cpp"],
  go: [SiGo, "language-go"], rust: [SiRust, "language-rust"], ruby: [SiRuby, "language-ruby"], react: [SiReact, "language-react"],
};

export default function LanguageBadge({ language }) {
  const [Icon, className] = languages[String(language || "").toLowerCase()] || [Code2, "language-code"];
  return <span className="language-badge"><span className={`language-icon ${className}`}><Icon aria-hidden="true" /></span>{language || "Code"}</span>;
}
