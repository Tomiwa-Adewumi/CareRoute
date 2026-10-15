"use client";

import { useEffect, useState } from "react";

type Recommendation = {
  hospital: {
    id: string;
    name: string;
    waitMinutes: number;
    travelMinutes: number;
    observedAt: string;
  };
  score: number;
  explanation: string;
};

const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export default function Home() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(`${apiBase}/api/recommendations`)
      .then((response) => {
        if (!response.ok) throw new Error("Recommendation API unavailable");
        return response.json();
      })
      .then(setRecommendations)
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <main className="shell">
      <section className="hero">
        <div className="eyebrow">Emergency care decision support</div>
        <h1>Find the fastest path to emergency care.</h1>
        <p>
          CareRoute ranks nearby emergency departments using travel time, reported wait time,
          and data freshness — then explains the tradeoff.
        </p>
        <div className="warning">
          CareRoute does not diagnose medical conditions. If you may be experiencing a medical emergency, call 911.
        </div>
      </section>

      <section className="results">
        <div className="sectionHeading">
          <div>
            <div className="eyebrow">Toronto MVP</div>
            <h2>Recommended emergency departments</h2>
          </div>
          <span className="pill">Explainable ranking</span>
        </div>

        {error && <div className="error">{error}. Start the Spring Boot API on port 8080.</div>}

        <div className="cards">
          {recommendations.map((item, index) => (
            <article className={`card ${index === 0 ? "best" : ""}`} key={item.hospital.id}>
              <div className="rank">#{index + 1}</div>
              <div className="cardBody">
                <div className="cardTop">
                  <h3>{item.hospital.name}</h3>
                  {index === 0 && <span className="recommended">Best option</span>}
                </div>
                <div className="metrics">
                  <div><strong>{item.hospital.travelMinutes}</strong><span>min travel</span></div>
                  <div><strong>{item.hospital.waitMinutes}</strong><span>min wait</span></div>
                  <div><strong>{Math.round(item.score)}</strong><span>CareRoute score</span></div>
                </div>
                <p className="explanation">{item.explanation}</p>
                <p className="freshness">Updated {new Date(item.hospital.observedAt).toLocaleTimeString()}</p>
              </div>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
