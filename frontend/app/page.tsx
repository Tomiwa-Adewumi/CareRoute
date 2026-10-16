"use client";

import { useEffect, useState } from "react";

type Recommendation = {
  hospital: {
    id: string;
    name: string;
    waitMinutes: number;
    predictedWaitMinutes: number;
    forecastSampleCount: number;
    forecastModel: string;
    travelMinutes: number;
    observedAt: string;
  };
  score: number;
  explanation: string;
};

const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://careroute-api-main.onrender.com";

export default function Home() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [locationStatus, setLocationStatus] = useState("Requesting your location…");

  const loadRecommendations = (lat?: number, lng?: number) => {
    setLoading(true);
    const query = lat !== undefined && lng !== undefined ? `?lat=${lat}&lng=${lng}` : "";
    return fetch(`${apiBase}/api/recommendations${query}`)
      .then((response) => {
        if (!response.ok) throw new Error("Recommendation API unavailable");
        return response.json();
      })
      .then((items: Recommendation[]) => {
        setRecommendations(items);
        setError(null);
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (!("geolocation" in navigator)) {
      setLocationStatus("Location is unavailable in this browser; showing waits without travel estimates.");
      void loadRecommendations();
      return;
    }

    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocationStatus("Using your current location for travel estimates.");
        void loadRecommendations(coords.latitude, coords.longitude);
      },
      () => {
        setLocationStatus("Location was not shared; showing waits without personalized travel estimates.");
        void loadRecommendations();
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }, []);

  return (
    <main className="shell">
      <section className="hero">
        <div className="eyebrow">Emergency care decision support</div>
        <h1>Find the fastest path to emergency care.</h1>
        <p>
          CareRoute ranks nearby emergency departments using travel time, reported wait time,
          a wait-at-arrival forecast, and data freshness — then explains the tradeoff.
        </p>
        <div className="warning">
          CareRoute does not diagnose medical conditions. If you may be experiencing a medical emergency, call 911.
        </div>

        <div className="locationStatus">{locationStatus}</div>
        <aside className="modelCard">
          <div className="eyebrow">AI / ML in this project</div>
          <strong>Explainable wait-at-arrival forecasting</strong>
          <p>CareRoute trains a bounded linear-regression model on each hospital’s latest observations. It projects the wait when you are expected to arrive, then combines that prediction with travel time and data freshness. No LLM makes medical decisions.</p>
        </aside>
      </section>

      <section className="results">
        <div className="sectionHeading">
          <div>
            <div className="eyebrow">Toronto MVP</div>
            <h2>Recommended emergency departments</h2>
          </div>
          <span className="pill">Explainable ranking</span>
        </div>

        {loading && <div className="error">Loading current Toronto emergency department data…</div>}
        {error && <div className="error">{error}. The free API may need about a minute to wake up.</div>}
        {!loading && !error && recommendations.length === 0 && (
          <div className="error">Current Toronto wait-time data is temporarily unavailable. Please try again shortly.</div>
        )}

        <div className="cards">
          {recommendations.map((item, index) => (
            <article className={`card ${index === 0 ? "best" : ""}`} key={item.hospital.id}>
              <div className="rank">#{index + 1}</div>
              <div className="cardBody">
                <div className="cardTop">
                  <h3>{item.hospital.name}</h3>
                  {index === 0 && <span className="recommended">Best option</span>}
                </div>
                <div className="metrics four">
                  <div><strong>{item.hospital.travelMinutes}</strong><span>min travel</span></div>
                  <div><strong>{item.hospital.waitMinutes}</strong><span>reported wait</span></div>
                  <div><strong>{item.hospital.predictedWaitMinutes}</strong><span>predicted at arrival</span></div>
                  <div><strong>{Math.round(item.score)}</strong><span>CareRoute score</span></div>
                </div>
                <p className="explanation">{item.explanation}</p>
                <p className="freshness">Model: {item.hospital.forecastModel.replaceAll("_", " ")} · {item.hospital.forecastSampleCount} samples · Updated {new Date(item.hospital.observedAt).toLocaleTimeString()}</p>
              </div>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
