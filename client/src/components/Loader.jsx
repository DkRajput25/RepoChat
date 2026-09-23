export default function Loader({ fullScreen = false, label = "Loading..." }) {
  return <div className={fullScreen ? "loader full-screen" : "loader"}><span className="spinner" />{label}</div>;
}
