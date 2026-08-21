import { useState } from "react";
import { clearActivity, formatActualCost, getActivity } from "../lib/activity";

function formatTimestamp(timestamp: number): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(timestamp);
}

export default function Activity() {
  const [activity, setActivity] = useState(getActivity);
  const actualCost = activity.reduce((sum, entry) => sum + (entry.costInUsdTicks ?? 0), 0);

  const removeAll = () => {
    if (!window.confirm("Clear this device's Grokked activity history?")) return;
    clearActivity();
    setActivity([]);
  };

  return (
    <div className="page activity-page">
      <header className="page-header activity-header">
        <div>
          <h1>Activity</h1>
          <p className="subtitle">Generations made in Grokked on this browser. Prompts, source images, API keys, and media URLs are never saved here.</p>
        </div>
        {activity.length > 0 && <button type="button" className="text-button" onClick={removeAll}>Clear history</button>}
      </header>
      <section className="activity-summary" aria-label="Activity summary">
        <span>Actual xAI cost returned</span>
        <strong>{formatActualCost(actualCost)}</strong>
        <small>Across {activity.length} {activity.length === 1 ? "generation" : "generations"} stored on this device</small>
      </section>
      {activity.length === 0 ? (
        <div className="activity-empty">No Grokked generations recorded yet. Completed work will appear here.</div>
      ) : (
        <div className="activity-table-wrap">
          <table className="activity-table">
            <thead><tr><th scope="col">When</th><th scope="col">Generation</th><th scope="col">Settings</th><th scope="col">Status</th><th scope="col">Actual cost</th></tr></thead>
            <tbody>
              {activity.map((entry) => (
                <tr key={entry.id}>
                  <td>{formatTimestamp(entry.createdAt)}</td>
                  <td><strong>{entry.kind}</strong><span>{entry.model}</span></td>
                  <td>{entry.settings}</td>
                  <td><span className={`activity-status activity-status--${entry.status}`}>{entry.status.replace("-", " ")}</span></td>
                  <td>{formatActualCost(entry.costInUsdTicks)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
