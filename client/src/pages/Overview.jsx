import { useEffect, useMemo, useState } from "react";
import { AlertCircle, CheckCircle2, FolderGit2, LoaderCircle, MessageSquareCode } from "lucide-react";
import Loader from "../components/Loader";
import { api } from "../services/api";
import "./dashboard-pages.css";

function Stat({ icon: Icon, label, value, hint }) {
  return <article className="stat-card"><div><p>{label}</p><strong>{value}</strong><small>{hint}</small></div><span><Icon /></span></article>;
}
export default function Overview() {
  const [repos, setRepos] = useState([]); const [error, setError] = useState(""); const [loading, setLoading] = useState(true);
  useEffect(() => { api.getRepositories(false).then(setRepos).catch((e) => setError(e.message)).finally(() => setLoading(false)); }, []);
  const stats = useMemo(() => ({ ready: repos.filter((r) => r.indexStatus === "READY").length, indexing: repos.filter((r) => r.indexStatus === "INDEXING").length, failed: repos.filter((r) => r.indexStatus === "FAILED").length, chunks: repos.reduce((sum, r) => sum + (r.chunkCount || 0), 0) }), [repos]);
  if (loading) return <Loader label="Loading workspace overview..." />; if (error) return <div className="notice error">{error}</div>;
  return <section className="overview-page"><header className="overview-heading"><h1>Overview</h1><p className="muted">Workspace stats and recent repository activity</p></header><div className="stat-grid"><Stat icon={FolderGit2} label="Repositories" value={repos.length} hint="Connected from GitHub" /><Stat icon={CheckCircle2} label="Ready to chat" value={stats.ready} hint={`${stats.indexing} currently indexing`} /><Stat icon={MessageSquareCode} label="Indexed chunks" value={stats.chunks.toLocaleString()} hint="Searchable code segments" /><Stat icon={stats.failed ? AlertCircle : LoaderCircle} label="Needs attention" value={stats.failed} hint={stats.failed ? "Review failed indexing jobs" : "All repos healthy"} /></div><div className="overview-columns"><section className="overview-card"><h2>Workspace status</h2><p className="muted">A quick snapshot of indexing across your connected repositories.</p><dl className="status-list"><div><dt>Ready</dt><dd>{stats.ready}</dd></div><div><dt>Indexing</dt><dd>{stats.indexing}</dd></div><div><dt>Pending</dt><dd>{repos.filter((r) => r.indexStatus === "PENDING").length}</dd></div><div><dt>Failed</dt><dd>{stats.failed}</dd></div></dl></section><section className="overview-card"><h2>Recent repositories</h2><p className="muted">Repositories most recently indexed in your workspace.</p><div className="recent-list">{repos.slice().sort((a, b) => new Date(b.indexedAt || 0) - new Date(a.indexedAt || 0)).slice(0, 3).map((repo) => <div key={repo.id}><FolderGit2 /><span>{repo.fullName}</span><small>{repo.indexStatus}</small></div>)}{repos.length === 0 && <p className="muted">No repositories yet. Sync GitHub repositories to get started.</p>}</div></section></div></section>;
}
