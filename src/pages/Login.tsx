import { useState } from "react";

export default function Login({ onLogin }: { onLogin: (key: string, remember: boolean) => void }) {
  const [key, setKey] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!key.trim()) {
      setError("Enter your xAI API key to continue.");
      return;
    }
    onLogin(key, remember);
  };

  return (
    <div className="page login-page">
      <section className="login-card">
        <h1>Grokked</h1>
        <p className="subtitle">A small, pay-as-you-go front end for your own xAI API key.</p>
        <form onSubmit={submit} className="form">
          <label className="block">
            <span>xAI API key</span>
            <input type="password" className="api-key-input" placeholder="Paste your API key" value={key} onChange={(event) => setKey(event.target.value)} autoComplete="off" autoFocus />
          </label>
          <label className="remember-key">
            <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
            <span>Remember API key on this device</span>
          </label>
          <p className="key-note">By default, your key remains only in this browser session. Remembering it stores the key in this browser’s local storage.</p>
          {error && <p className="error">{error}</p>}
          <button type="submit" className="primary-button">Continue</button>
        </form>
        <p className="login-help">Create a key in the <a href="https://console.x.ai" target="_blank" rel="noreferrer">xAI Console</a>. Grokked sends generation requests through its restricted xAI proxy; it never stores your key on its server.</p>
      </section>
    </div>
  );
}
