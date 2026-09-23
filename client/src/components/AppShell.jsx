import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { FolderGit2, LayoutGrid, LogOut, Moon, Settings, Sun } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../hooks/useAuth";
import { api } from "../services/api";

export default function AppShell() {
  const { user } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const [light, setLight] = useState(false);
  const logout = async () => { try { await api.logout(); } finally { navigate("/login"); } };
  return <div className={`app-shell ${light ? "light" : ""}`}>
    <aside className="sidebar">
      <Link className="brand sidebar-brand" to="/dashboard"><span className="brand-mark">RC</span><span>RepoChat<small>Chat with your code</small></span></Link>
      <nav className="sidebar-nav"><p>Workspace</p><Link to="/dashboard/overview" className={`nav-item ${location.pathname === "/dashboard/overview" ? "active" : ""}`}><LayoutGrid /> Overview</Link><Link to="/dashboard" className={`nav-item ${location.pathname === "/dashboard" ? "active" : ""}`}><FolderGit2 /> Repositories</Link><p>Account</p><Link to="/dashboard/settings" className={`nav-item ${location.pathname === "/dashboard/settings" ? "active" : ""}`}><Settings /> Settings</Link></nav>
      <div className="sidebar-user">{user?.avatarUrl ? <img src={user.avatarUrl} alt="" className="avatar" /> : <span className="avatar avatar-fallback">{(user?.displayName || "U")[0]}</span>}<div><strong>{user?.displayName || user?.githubUsername}</strong><small>@{user?.githubUsername}</small></div><button className="button ghost icon-button" onClick={logout} title="Sign out"><LogOut /></button></div>
    </aside>
    <div className="app-main"><header className="topbar"><Link className="mobile-brand brand" to="/dashboard"><span className="brand-mark">RC</span> RepoChat</Link><div className="user-menu"><button className="theme-button" onClick={() => setLight(!light)} type="button" aria-label="Toggle theme">{light ? <Moon /> : <Sun />}</button></div></header><main className="content"><Outlet /></main></div>
  </div>;
}
