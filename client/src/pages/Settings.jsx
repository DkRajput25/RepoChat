import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { LogOut, Moon, Sun, UserRound } from "lucide-react";
import { FaGithub } from "react-icons/fa";
import { useAuth } from "../hooks/useAuth";
import { api } from "../services/api";
import "./dashboard-pages.css";

export default function Settings() {
  const { user } = useAuth(); const navigate = useNavigate(); const [dark, setDark] = useState(true); const [leaving, setLeaving] = useState(false);
  const logout = async () => { setLeaving(true); try { await api.logout(); } finally { navigate("/login"); } };
  return <section className="settings-page"><header className="overview-heading"><h1>Settings</h1><p className="muted">Profile, appearance, and account preferences</p></header><article className="settings-card"><h2>Profile</h2><p className="muted">Your GitHub account connected to RepoChat.</p><div className="profile-row">{user?.avatarUrl ? <img className="profile-avatar" src={user.avatarUrl} alt="" /> : <span className="profile-avatar"><UserRound /></span>}<div><strong>{user?.displayName}</strong><small>@{user?.githubUsername}</small></div></div><dl className="profile-data"><div><dt>Display name</dt><dd>{user?.displayName || "—"}</dd></div><div><dt>GitHub username</dt><dd>@{user?.githubUsername || "—"}</dd></div><div><dt>Authentication</dt><dd><FaGithub /> GitHub OAuth</dd></div></dl></article><article className="settings-card"><h2>Appearance</h2><p className="muted">Customize how RepoChat looks on your device.</p><div className="setting-action"><div><strong>Dark mode</strong><small>Switch between light and dark themes.</small></div><button type="button" className={`switch ${dark ? "on" : ""}`} onClick={() => setDark(!dark)} aria-label="Toggle dark mode"><span /></button><Sun /><Moon /></div></article><article className="settings-card"><h2>Account actions</h2><p className="muted">Manage your session and connected workspace.</p><button type="button" className="button danger" onClick={logout} disabled={leaving}><LogOut /> {leaving ? "Signing out..." : "Log out"}</button></article></section>;
}
