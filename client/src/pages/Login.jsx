import { useState } from "react";
import { useLocation } from "react-router-dom";
import { Sun } from "lucide-react";
import { FaGithub } from "react-icons/fa";
import { api } from "../services/api";
export default function Login() {
  const [loading, setLoading] = useState(false); const [error, setError] = useState(""); const location = useLocation();
  const signIn = async () => { setLoading(true); setError(""); try { const { url } = await api.getLoginUrl(); window.location.assign(`${api.baseUrl}${url}`); } catch (err) { setError(err.message); setLoading(false); } };
  return <div className="auth-page login-screen"><div className="login-brand brand"><span className="brand-mark">RC</span> RepoChat</div><button className="theme-button login-theme" type="button" aria-label="Toggle theme"><Sun /></button><div className="auth-card"><div className="github-logo"><FaGithub /></div><h1>Sign in to RepoChat</h1><p className="muted">Connect GitHub to chat with your repositories.</p>{location.search && <p className="error">GitHub sign-in was not completed. Please try again.</p>}{error && <p className="error">{error}</p>}<button className="button github-button wide" onClick={signIn} disabled={loading}><FaGithub />{loading ? "Connecting..." : "Continue with GitHub"}</button></div></div>;
}
