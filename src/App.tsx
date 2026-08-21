import { useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { setGrokApiKey } from "./lib/grokApi";
import { forgetApiKey, getRememberedApiKey, rememberApiKey } from "./lib/keyStorage";
import Login from "./pages/Login";
import ImageToImage from "./pages/ImageToImage";
import TextToImage from "./pages/TextToImage";
import VideoGeneration from "./pages/VideoGeneration";
import Activity from "./pages/Activity";
import NotFound from "./pages/NotFound";
import "./App.css";

const SITE_TITLE = "Grokked";
const PAGE_TITLES: Record<string, string> = {
  "/": "Image to image",
  "/login": "Sign in",
  "/text-to-image": "Text to image",
  "/text-to-video": "Text to video",
  "/image-to-video": "Image to video",
  "/activity": "Activity",
};

function usePageTitle() {
  const { pathname } = useLocation();
  useEffect(() => {
    document.title = `${PAGE_TITLES[pathname] ?? "Not found"} — ${SITE_TITLE}`;
  }, [pathname]);
}

function ProtectedLayout({ authenticated, onLogout, children }: { authenticated: boolean; onLogout: () => void; children: React.ReactNode }) {
  if (!authenticated) return <Navigate to="/login" replace />;
  return (
    <>
      <nav className="nav" aria-label="Main navigation">
        <NavLink className="nav-brand" to="/text-to-video">Grokked</NavLink>
        <NavLink to="/text-to-video">Text to video</NavLink>
        <NavLink to="/image-to-video">Image to video</NavLink>
        <NavLink to="/text-to-image">Text to image</NavLink>
        <NavLink to="/">Image to image</NavLink>
        <NavLink to="/activity">Activity</NavLink>
        <button type="button" className="nav-logout" onClick={onLogout}>Sign out</button>
      </nav>
      <main>{children}</main>
    </>
  );
}

export default function App() {
  const [apiKey, setApiKey] = useState(() => getRememberedApiKey());
  usePageTitle();

  useEffect(() => {
    setGrokApiKey(apiKey);
  }, [apiKey]);

  const handleLogin = (key: string, shouldRemember: boolean) => {
    if (shouldRemember) rememberApiKey(key);
    else forgetApiKey();
    setApiKey(key.trim());
  };

  const handleLogout = () => {
    forgetApiKey();
    setApiKey(null);
  };

  const protectedPage = (child: React.ReactNode) => (
    <ProtectedLayout authenticated={Boolean(apiKey)} onLogout={handleLogout}>{child}</ProtectedLayout>
  );

  return (
    <div className="app-layout">
      <div className="app-content">
        <Routes>
          <Route path="/login" element={apiKey ? <Navigate to="/text-to-video" replace /> : <Login onLogin={handleLogin} />} />
          <Route path="/" element={protectedPage(<ImageToImage />)} />
          <Route path="/text-to-image" element={protectedPage(<TextToImage />)} />
          <Route path="/text-to-video" element={protectedPage(<VideoGeneration mode="text" />)} />
          <Route path="/image-to-video" element={protectedPage(<VideoGeneration mode="image" />)} />
          <Route path="/activity" element={protectedPage(<Activity />)} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
      <footer className="footer"><a href="https://github.com/hanenashi/grokked" target="_blank" rel="noreferrer">Source</a></footer>
    </div>
  );
}
