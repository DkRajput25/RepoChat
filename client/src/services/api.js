const API_URL = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: "include",
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.message || body.error || message;
    } catch {
      // Some Spring error responses have no JSON body.
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  baseUrl: API_URL,
  getMe: () => request("/api/auth/me"),
  getLoginUrl: () => request("/api/auth/login-url"),
  logout: () => request("/api/auth/logout", { method: "POST" }),
  getRepositories: (refresh = true) => request(`/api/repos?refresh=${refresh}`),
  getRepository: (id) => request(`/api/repos/${id}`),
  startIndex: (id) => request(`/api/repos/${id}/index`, { method: "POST" }),
  getRepositoryStatus: (id) => request(`/api/repos/${id}/status`),
  createSession: (repositoryId, title) =>
    request("/api/chat/sessions", {
      method: "POST",
      body: JSON.stringify({ repositoryId, ...(title ? { title } : {}) }),
    }),
  getSessions: (repositoryId) => request(`/api/chat/sessions?repositoryId=${repositoryId}`),
  getMessages: (sessionId) => request(`/api/chat/sessions/${sessionId}`),
};

export async function streamMessage(sessionId, content, handlers) {
  const response = await fetch(`${API_URL}/api/chat/sessions/${sessionId}/messages`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify({ content }),
  });
  if (!response.ok) {
    throw new Error(`Chat request failed (${response.status})`);
  }
  if (!response.body) throw new Error("The chat stream did not return a response body.");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";

  const handleEvent = (rawEvent) => {
    let data = "";
    rawEvent.split("\n").forEach((line) => {
      if (line.startsWith("event:")) eventName = line.slice(6).trim();
      if (line.startsWith("data:")) data += line.slice(5).trim();
    });
    if (!data) return;
    let value = data;
    try {
      value = JSON.parse(data);
    } catch {
      // The done event is plain text.
    }
    if (eventName === "user_message") handlers.onUserMessage?.(value);
    if (eventName === "token") handlers.onToken?.(value);
    if (eventName === "assistant_message") handlers.onAssistantMessage?.(value);
    if (eventName === "done") handlers.onDone?.();
    eventName = "message";
  };

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || "";
    events.forEach(handleEvent);
    if (done) break;
  }
  if (buffer.trim()) handleEvent(buffer);
}
