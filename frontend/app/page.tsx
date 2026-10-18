"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type Location = { lat: number; lng: number };
type Recommendation = {
  hospital: {
    id: string; name: string; waitMinutes: number | null; forecastModel: string;
    travelMinutes: number | null; travelDistanceKm: number | null; travelSource: string;
    observedAt: string | null; timestampSource: string;
  };
  score: number | null;
  scoreBreakdown: { predictedWaitMinutes: number; travelMinutes: number; freshnessPenaltyMinutes: number } | null;
  explanation: string; status: "AVAILABLE" | "UNAVAILABLE";
};
type Metrics = { maeMinutes: number; rmseMinutes: number; medianAbsoluteErrorMinutes: number };
type ModelEvaluation = {
  status: string; evaluatedPredictions: number; storedObservations: number;
  linearRegression: Metrics | null; latestWaitBaseline: Metrics | null;
  movingAverageBaseline: Metrics | null; exponentialSmoothing: Metrics | null;
  intervalCoveragePercent: number | null; minimumPredictionsForReporting: number; methodology: string;
};
const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export default function Home() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [locationStatus, setLocationStatus] = useState("Enable location for travel estimates");
  const [userLocation, setUserLocation] = useState<Location | null>(null);
  const [evaluation, setEvaluation] = useState<ModelEvaluation | null>(null);
  const [evaluationError, setEvaluationError] = useState(false);
  const [checkedAt, setCheckedAt] = useState<number | null>(null);
  const request = useRef<AbortController | null>(null);
  const locationRequest = useRef(0);
  const hasLocation = userLocation !== null;

  const refresh = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(30000)]);
    setLoading(true);
    const query = userLocation ? `?lat=${userLocation.lat}&lng=${userLocation.lng}` : "";
    const metricsRequest = fetch(`${apiBase}/api/model/metrics`, { signal, cache: "no-store" })
      .then(response => { if (!response.ok) throw new Error(); return response.json(); })
      .then((result: ModelEvaluation) => {
        if (!controller.signal.aborted) { setEvaluation(result); setEvaluationError(false); }
      })
      .catch(() => {
        if (!controller.signal.aborted) { setEvaluation(null); setEvaluationError(true); }
      });
    try {
      const response = await fetch(`${apiBase}/api/recommendations${query}`, { signal, cache: "no-store" });
      if (!response.ok) throw new Error("Wait comparison is temporarily unavailable");
      const items: Recommendation[] = await response.json();
      if (!controller.signal.aborted) {
        setRecommendations(items); setError(null); setCheckedAt(Date.now());
      }
    } catch {
      if (!controller.signal.aborted) {
        setRecommendations([]);
        setError("Could not refresh current data. Previous rankings have been removed. Please retry.");
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
    await metricsRequest;
  }, [userLocation]);

  useEffect(() => {
    const initial = setTimeout(() => void refresh(), 0);
    const timer = setInterval(() => { if (!document.hidden) void refresh(); }, 60000);
    const onFocus = () => { if (!document.hidden) void refresh(); };
    document.addEventListener("visibilitychange", onFocus);
    window.addEventListener("focus", onFocus);
    return () => {
      clearTimeout(initial); clearInterval(timer); request.current?.abort();
      document.removeEventListener("visibilitychange", onFocus);
      window.removeEventListener("focus", onFocus);
    };
  }, [refresh]);

  const requestLocation = () => {
    const generation = ++locationRequest.current;
    if (!("geolocation" in navigator)) {
      setLocationStatus("Location unavailable · travel excluded");
      setUserLocation(null); return;
    }
    setLocationStatus("Requesting your location…");
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        if (generation !== locationRequest.current) return;
        setRecommendations([]); setLoading(true);
        setUserLocation({ lat: coords.latitude, lng: coords.longitude });
        setLocationStatus("Location enabled · refresh location");
      },
      () => {
        if (generation !== locationRequest.current) return;
        setUserLocation(null); setLocationStatus("Location not shared · travel excluded");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  };

  // Defensive expiry also applies if a cached or older API returns an outdated AVAILABLE row.
  const available = recommendations.filter(item => item.status === "AVAILABLE" && item.score !== null
    && item.hospital.observedAt !== null && checkedAt !== null
    && checkedAt - Date.parse(item.hospital.observedAt) <= 30 * 60000);
  const unavailable = recommendations.filter(item => !available.includes(item));
  const reportable = evaluation?.status === "EVALUATED"
    && evaluation.evaluatedPredictions >= evaluation.minimumPredictionsForReporting;

  return <main>
    <header className="topbar">
      <a className="brand" href="#top" aria-label="CareRoute home"><span className="brandMark">C</span><span>CareRoute</span></a>
      <nav aria-label="Page navigation"><a href="#recommendations">Compare waits</a><a href="#model">How it works</a></nav>
      <span className="prototypeBadge">Toronto prototype</span>
    </header>
    <section className="hero" id="top">
      <div className="heroCopy">
        <div className="eyebrow light">Reported waits, with context</div>
        <h1>Compare available waits and estimated travel.</h1>
        <p className="heroLead">Explore reporting adult emergency departments in Toronto. Scores combine the latest reported wait, optional travel estimates, and a freshness penalty. They do not predict your individual time to care.</p>
        <div className="heroActions">
          <a className="primaryButton" href="#recommendations">Compare reported waits ↓</a>
          <button className="locationButton" onClick={requestLocation} type="button">{locationStatus}</button>
          {hasLocation && <button className="locationButton" onClick={() => {
            locationRequest.current++; request.current?.abort(); setRecommendations([]);
            setUserLocation(null); setLocationStatus("Location disabled · travel excluded");
          }} type="button">Disable location</button>}
        </div>
        <p className="privacyNote">Location is optional. When enabled, coordinates are kept in this tab and sent to our API on each refresh, then shared with OSRM for routing. Rounded coordinates are cached in server memory with five-minute expiry, cleanup every minute, and a 1,000-entry limit. Our application does not write coordinates to its database. Hosting and routing providers may process request logs.</p>
      </div>
      <div className="heroModel" aria-label="CareRoute comparison pipeline">
        <div className="modelHeader"><span>Comparison pipeline</span><span className="liveTag">Reported data</span></div>
        <div className="pipeline">
          <div><span className="stepNumber">01</span><strong>Check</strong><small>Availability and freshness</small></div>
          <div><span className="stepNumber">02</span><strong>Compare</strong><small>Latest reported wait</small></div>
          <div><span className="stepNumber">03</span><strong>Explain</strong><small>Wait + travel + freshness</small></div>
        </div>
        <div className="formulaCard"><span>Comparison score</span><code>wait + travel + 0.2 × age in minutes</code><p>Only available data aged 30 minutes or less can receive a score. A score is not a promised wait.</p></div>
      </div>
    </section>
    <aside className="safetyBar"><strong>Medical safety:</strong> CareRoute does not diagnose or replace emergency services. If someone may be experiencing a medical emergency, call 911. Emergency departments triage by severity. This adult comparison does not assess clinical suitability.</aside>
    <section className="results section" id="recommendations">
      <div className="sectionHeading">
        <div><div className="eyebrow">Available reporting facilities</div><h2>Compare reported waits</h2><p>Coverage is incomplete. Unavailable facilities remain visible below without a score.</p></div>
        <button className="locationButton" onClick={() => void refresh()} type="button">Refresh data</button>
      </div>
      <p className="dataPill">Page checks every minute; source ingestion runs every 10 minutes.{checkedAt !== null && ` Last checked ${new Date(checkedAt).toLocaleString()}.`}</p>
      <div className={`personalizationBanner ${hasLocation ? "active" : ""}`}>
        {hasLocation ? "Personalized comparison is active: known travel estimates are included. Routes do not include live traffic." : "Location is not active: travel is excluded. Enable location to compare travel estimates."}
      </div>
      {loading && <div className="notice loadingNotice" role="status">Refreshing current data…</div>}
      {error && <div className="notice errorNotice" role="alert">{error}</div>}
      {!loading && !error && <>
        {available.length === 0 && <div className="notice">No facilities currently have enough verified availability and fresh data for a comparison.</div>}
        <div className="cards">
          {available.map((item, index) => <article className="card" key={item.hospital.id}>
            <div className="cardHeader">
              <div className="rankBlock"><span>Order</span><strong>{String(index + 1).padStart(2, "0")}</strong></div>
              <div className="hospitalTitle"><span className="modelState">Latest-wait baseline</span><h3>{item.hospital.name}</h3></div>
              <div className="scoreBlock"><strong>{Math.round(item.score!)}</strong><span>comparison score</span></div>
            </div>
            <div className="metricGrid">
              <div><span>{hasLocation ? "Estimated travel" : "Travel excluded"}</span><strong>{hasLocation ? item.hospital.travelMinutes : "—"}<small>{hasLocation ? " min" : ""}</small></strong><em>{hasLocation ? `${item.hospital.travelDistanceKm} km · ${item.hospital.travelSource === "OSRM_ROUTE" ? "road route" : "distance fallback"}` : "location optional"}</em></div>
              <div><span>Reported wait</span><strong>{item.hospital.waitMinutes}<small> min</small></strong></div>
              <div><span>Data age</span><strong>{Math.max(0, Math.floor((checkedAt! - Date.parse(item.hospital.observedAt!)) / 60000))}<small> min</small></strong></div>
            </div>
            {item.scoreBreakdown && <div className="scoreFormula"><span>{item.scoreBreakdown.predictedWaitMinutes}m reported</span><b>+</b><span>{item.scoreBreakdown.travelMinutes}m travel</span><b>+</b><span>{item.scoreBreakdown.freshnessPenaltyMinutes}m freshness</span><b>=</b><strong>{Math.round(item.score!)} score</strong></div>}
            <div className="explanationRow"><p>{item.explanation}</p><time dateTime={item.hospital.observedAt!}>{new Date(item.hospital.observedAt!).toLocaleString()}</time></div>
            {item.hospital.timestampSource === "ER_COMPASS_FETCH_TIME" && <p>Age is based on when the source fetched this value; the original measurement time is unknown. Excluded from model evaluation.</p>}
          </article>)}
        </div>
        {unavailable.length > 0 && <section aria-label="Unavailable facilities"><h3>Unavailable for comparison</h3><div className="cards">
          {unavailable.map(item => <article className="card" key={item.hospital.id}><h3>{item.hospital.name}</h3><p>{item.status === "AVAILABLE" ? "Data is more than 30 minutes old" : item.explanation}</p><strong>No score</strong></article>)}
        </div></section>}
      </>}
    </section>
    <section className="modelSection" id="model">
      <div className="section modelLayout">
        <div className="modelIntro"><div className="eyebrow light">How it works</div><h2>Simple baseline.<br />Measured challengers.</h2><p>Comparisons use the latest reported wait. Regression and exponential smoothing are evaluated offline; neither controls the ranking. A future model must demonstrate improvement across hospitals and arrival horizons before promotion.</p><a href="https://github.com/Tomiwa-Adewumi/CareRoute/blob/main/docs/forecasting.md" target="_blank" rel="noreferrer">Read the model documentation ↗</a></div>
        <div className="mathPanel">
          <div className="mathStep"><span>1</span><div><strong>Check eligibility</strong><p>Open adult emergency department, available wait, and data at most 30 minutes old.</p></div></div>
          <div className="mathStep"><span>2</span><div><strong>Use reported waits</strong><p>The latest-wait baseline is the production default. Unknown travel is never treated as a free trip.</p></div></div>
          <div className="mathStep"><span>3</span><div><strong>Evaluate separately</strong><p>Only verified source timestamps enter the primary benchmark. Reconstructed history and source-fetch timestamps are excluded.</p></div></div>
          <div className="mathStep"><span>4</span><div><strong>Keep uncertainty explicit</strong><p>Regression ranges are heuristic, without a guaranteed coverage level. These experiments do not validate clinical suitability or hospital rankings.</p></div></div>
        </div>
      </div>
      <div className="section evidenceSection">
        <div className="evidenceHeading"><h3>Exploratory model benchmark</h3><span className="evaluationState">{evaluationError ? "UNAVAILABLE" : evaluation?.status.replaceAll("_", " ") ?? "LOADING"}</span></div>
        <p className="evidenceCopy">{evaluationError ? "Evaluation could not be refreshed." : evaluation?.methodology ?? "Loading evaluation status…"}</p>
        <div className="evidenceGrid">
          <div><span>Eligible observations</span><strong>{evaluation?.storedObservations ?? "—"}</strong><small>verified source timestamps</small></div>
          <div><span>Evaluated predictions</span><strong>{evaluation?.evaluatedPredictions ?? "—"}</strong></div>
          <div><span>Regression MAE</span><strong>{reportable && evaluation?.linearRegression ? `${evaluation.linearRegression.maeMinutes}m` : "—"}</strong><small>latest-wait baseline {reportable && evaluation?.latestWaitBaseline ? `${evaluation.latestWaitBaseline.maeMinutes}m` : "—"}</small></div>
          <div><span>Smoothing MAE</span><strong>{reportable && evaluation?.exponentialSmoothing ? `${evaluation.exponentialSmoothing.maeMinutes}m` : "—"}</strong><small>moving average {reportable && evaluation?.movingAverageBaseline ? `${evaluation.movingAverageBaseline.maeMinutes}m` : "—"}</small></div>
          <div><span>Heuristic range coverage</span><strong>{reportable && evaluation?.intervalCoveragePercent != null ? `${evaluation.intervalCoveragePercent}%` : "—"}</strong><small>observed coverage, not a 95% guarantee</small></div>
        </div>
        <p className="honestyNote">Errors and coverage are withheld until {evaluation?.minimumPredictionsForReporting ?? 10} eligible predictions exist. That reporting threshold does not establish model validity.</p>
      </div>
    </section>
    <footer><div><strong>CareRoute</strong><span>Reported waits with transparent limits.</span></div><p>Prototype only · Not medical advice · Toronto, Ontario</p></footer>
  </main>;
}
