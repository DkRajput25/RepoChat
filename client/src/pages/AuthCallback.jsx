import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Loader from "../components/Loader";
import { api } from "../services/api";

export default function AuthCallback() {
  const navigate = useNavigate();
  useEffect(() => {
    api.getMe().then(() => navigate("/dashboard", { replace: true }))
      .catch(() => navigate("/login?error=oauth_failed", { replace: true }));
  }, [navigate]);
  return <Loader fullScreen label="Finishing GitHub sign-in..." />;
}
