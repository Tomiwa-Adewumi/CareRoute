"use client";

import { useCallback, useEffect, useState } from "react";

type Recommendation = { hospital: { id: string; name: string; waitMinutes: number; predictedWaitMinutes: number; predictionLowerMinutes: number; predictionUpperMinutes: number; forecastSampleCount: number; forecastModel: string; travelMinutes: number; observedAt: string }; score: number; explanation: string };
type Metrics = { maeMinutes: number; rmseMinutes: number; medianAbsoluteErrorMinutes: number };
type ModelEvaluation = { status: string; evaluatedPredictions: number; storedObservations: number; hospitalsWithActiveForecast: number; newestObservation: string | null; linearRegression: Metrics | null; latestWaitBaseline: Metrics | null; movingAverageBaseline: Metrics | null; intervalCoveragePercent: number | null; minimumPredictionsForReporting: number; methodology: string };
const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://careroute-api-main.onrender.com";

export default function Home() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [locationStatus, setLocationStatus] = useState("Requesting your location…");
  const [hasLocation, setHasLocation] = useState(false);
  const [evaluation, setEvaluation] = useState<ModelEvaluation | null>(null);

  const loadRecommendations = useCallback((lat?: number, lng?: number) => {
    setLoading(true);
    const query = lat !== undefined && lng !== undefined ? `?lat=${lat}&lng=${lng}` : "";
    return fetch(`${apiBase}/api/recommendations${query}`)
      .then((response) => { if (!response.ok) throw new Error("Recommendation API unavailable"); return response.json(); })
      .then((items: Recommendation[]) => { setRecommendations(items); setError(null); })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const requestLocation = useCallback(() => {
    if (!("geolocation" in navigator)) {
      setLocationStatus("Location unavailable · showing reported waits");
      setHasLocation(false);
      void loadRecommendations();
      return;
    }
    setLocationStatus("Requesting your location…");
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => { setHasLocation(true); setLocationStatus("Location enabled · used only for this request"); void loadRecommendations(coords.latitude, coords.longitude); },
      () => { setHasLocation(false); setLocationStatus("Location not shared · showing non-personalized estimates"); void loadRecommendations(); },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }, [loadRecommendations]);

  useEffect(() => {
    requestLocation();
    fetch(`${apiBase}/api/model/metrics`)
      .then((response) => response.ok ? response.json() : null)
      .then((result: ModelEvaluation | null) => setEvaluation(result))
      .catch(() => setEvaluation(null));
  }, [requestLocation]);

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="#top" aria-label="CareRoute home"><span className="brandMark">C</span><span>CareRoute</span></a>
        <nav aria-label="Page navigation"><a href="#recommendations">Recommendations</a><a href="#model">How the model works</a></nav>
        <span className="prototypeBadge">Toronto prototype</span>
      </header>

      <section className="hero" id="top">
        <div className="heroCopy">
          <div className="eyebrow light">Explainable emergency-care navigation</div>
          <h1>Not just the shortest wait. The shortest path to care.</h1>
          <p className="heroLead">CareRoute forecasts the wait when you arrive, adds travel time and data freshness, then shows exactly why each emergency department ranked where it did.</p>
          <div className="heroActions">
            <a className="primaryButton" href="#recommendations">See live recommendations <span>↓</span></a>
            <button className="locationButton" onClick={requestLocation} type="button"><span className={`statusDot ${hasLocation ? "active" : ""}`} />{locationStatus}</button>
          </div>
          <p className="privacyNote">Your coordinates are sent with one API request and are never stored.</p>
        </div>

        <div className="heroModel" aria-label="CareRoute scoring pipeline">
          <div className="modelHeader"><span>Decision pipeline</span><span className="liveTag"><i /> Live data</span></div>
          <div className="pipeline">
            <div><span className="stepNumber">01</span><strong>Observe</strong><small>Recent wait-time history</small></div>
            <div><span className="stepNumber">02</span><strong>Forecast</strong><small>Per-hospital OLS regression</small></div>
            <div><span className="stepNumber">03</span><strong>Rank</strong><small>Wait + travel + freshness</small></div>
          </div>
          <div className="formulaCard"><span>Ranking objective</span><code>S(h) = ŷ<sub>arrival</sub> + t<sub>travel</sub> + 0.2 · min(age, 120)</code><p>Lower is better. Every term is returned by the API.</p></div>
        </div>
      </section>

      <aside className="safetyBar"><strong>Medical safety:</strong> CareRoute does not diagnose or replace emergency services. If someone may be experiencing a medical emergency, call 911.</aside>

      <section className="results section" id="recommendations">
        <div className="sectionHeading">
          <div><div className="eyebrow">Live decision support</div><h2>Emergency departments, ranked</h2><p>Lower CareRoute scores indicate a shorter estimated total path to care.</p></div>
          <span className="dataPill"><i /> ER data refreshes every 10 min</span>
        </div>
        <div className={`personalizationBanner ${hasLocation ? "active" : ""}`}>
          <span className={`statusDot ${hasLocation ? "active" : ""}`} />
          {hasLocation
            ? "Personalized ranking is active: travel time from your current location is included in every score and forecast horizon below."
            : "Location is not active: travel is excluded. Enable location above to personalize the ranking."}
        </div>
        {loading && <div className="notice loadingNotice"><span className="spinner" /> Waking the API and loading Toronto data…</div>}
        {error && <div className="notice errorNotice">{error}. The free API may need about a minute to wake up.</div>}
        {!loading && !error && recommendations.length === 0 && <div className="notice errorNotice">Current Toronto wait-time data is temporarily unavailable. Please try again shortly.</div>}

        <div className="cards">
          {recommendations.map((item, index) => {
            const isForecast = item.hospital.forecastModel === "LINEAR_REGRESSION_V1";
            return <article className={`card ${index === 0 ? "best" : ""}`} key={item.hospital.id}>
              <div className="cardHeader">
                <div className="rankBlock"><span>Rank</span><strong>{String(index + 1).padStart(2, "0")}</strong></div>
                <div className="hospitalTitle"><div className="cardLabels">{index === 0 && <span className="recommended">Best estimated option</span>}<span className={`modelState ${isForecast ? "active" : ""}`}>{isForecast ? "Forecast active" : "Collecting history"}</span></div><h3>{item.hospital.name}</h3></div>
                <div className="scoreBlock"><strong>{Math.round(item.score)}</strong><span>total score</span></div>
              </div>
              <div className="metricGrid">
                <div><span>{hasLocation ? "Travel from you" : "Travel excluded"}</span><strong>{hasLocation ? item.hospital.travelMinutes : "—"}<small>{hasLocation ? " min" : ""}</small></strong></div>
                <div><span>Reported wait</span><strong>{item.hospital.waitMinutes}<small> min</small></strong></div>
                <div className="prediction"><span>Predicted at arrival</span><strong>{item.hospital.predictedWaitMinutes}<small> min</small></strong><em>{isForecast ? `${item.hospital.predictionLowerMinutes}–${item.hospital.predictionUpperMinutes} min interval` : "current-wait fallback"}</em></div>
                <div><span>Model evidence</span><strong>{item.hospital.forecastSampleCount}<small> samples</small></strong></div>
              </div>
              <div className="explanationRow"><p><b>Why this rank:</b> {item.explanation}</p><time dateTime={item.hospital.observedAt}>Updated {new Date(item.hospital.observedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</time></div>
            </article>;
          })}
        </div>
      </section>

      <section className="modelSection" id="model">
        <div className="section modelLayout">
          <div className="modelIntro"><div className="eyebrow light">Under the hood</div><h2>Small model.<br />Clear reasoning.</h2><p>CareRoute uses ordinary least squares—not an LLM—to learn a local trend for each hospital. The design favors interpretability, safe fallbacks, and measurable behavior.</p><a href="https://github.com/Tomiwa-Adewumi/CareRoute#the-math" target="_blank" rel="noreferrer">Read the model documentation ↗</a></div>
          <div className="mathPanel">
            <div className="mathStep"><span>1</span><div><strong>Represent the history</strong><p>x = minutes since the oldest observation<br />y = reported wait in minutes</p></div></div>
            <div className="mathStep"><span>2</span><div><strong>Fit the trend</strong><code>β₁ = Σ(xᵢ−x̄)(yᵢ−ȳ) / Σ(xᵢ−x̄)²</code></div></div>
            <div className="mathStep"><span>3</span><div><strong>Forecast arrival</strong><code>ŷ = ȳ + β₁(x<sub>arrival</sub> − x̄)</code></div></div>
            <div className="mathStep"><span>4</span><div><strong>Apply guardrails</strong><p>Require ≥3 samples; clamp predictions to [0, 720] and within ±60 minutes of the latest wait.</p></div></div>
          </div>
        </div>
        <div className="section evidenceSection">
          <div className="evidenceHeading"><div><div className="eyebrow light">Live model evidence</div><h3>{evaluation?.status === "EVALUATED" ? "Backtest results" : "Building an honest benchmark"}</h3></div><span className={`evaluationState ${evaluation?.status === "EVALUATED" ? "ready" : ""}`}>{evaluation?.status?.replaceAll("_", " ") ?? "LOADING"}</span></div>
          <p className="evidenceCopy">{evaluation?.methodology ?? "Loading evaluation status…"}. Metrics remain provisional until at least {evaluation?.minimumPredictionsForReporting ?? 10} out-of-sample predictions exist.</p>
          <div className="evidenceGrid">
            <div><span>Stored observations</span><strong>{evaluation?.storedObservations ?? "—"}</strong><small>deduplicated records</small></div>
            <div><span>Evaluated predictions</span><strong>{evaluation?.evaluatedPredictions ?? "—"}</strong><small>no future-data leakage</small></div>
            <div><span>Regression MAE</span><strong>{evaluation?.linearRegression ? `${evaluation.linearRegression.maeMinutes}m` : "—"}</strong><small>latest-wait baseline {evaluation?.latestWaitBaseline ? `${evaluation.latestWaitBaseline.maeMinutes}m` : "—"}</small></div>
            <div><span>Interval coverage</span><strong>{evaluation?.intervalCoveragePercent != null ? `${evaluation.intervalCoveragePercent}%` : "—"}</strong><small>residual-based 95% interval</small></div>
          </div>
          <p className="honestyNote">No performance claim is shown until the minimum evaluation sample is reached. This prevents a small demo dataset from being presented as model validation.</p>
        </div>
      </section>

      <footer><div><strong>CareRoute</strong><span>Explainable ML for a real public-service decision.</span></div><p>Prototype only · Not medical advice · Toronto, Ontario</p></footer>
    </main>
  );
}
