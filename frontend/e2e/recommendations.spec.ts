import { expect, test } from "@playwright/test";

test("uses location and explains a ranked recommendation", async ({ browser }) => {
  const context = await browser.newContext({
    permissions: ["geolocation"],
    geolocation: { latitude: 43.6532, longitude: -79.3832 },
  });
  const page = await context.newPage();
  await page.route("**/api/model/metrics", route => route.fulfill({ json: {
    status: "EVALUATED", evaluatedPredictions: 20, storedObservations: 48,
    hospitalsWithActiveForecast: 4, newestObservation: "2026-10-17T12:00:00Z",
    linearRegression: { maeMinutes: 12, rmseMinutes: 15, medianAbsoluteErrorMinutes: 10 },
    latestWaitBaseline: { maeMinutes: 18, rmseMinutes: 22, medianAbsoluteErrorMinutes: 16 },
    movingAverageBaseline: { maeMinutes: 16, rmseMinutes: 20, medianAbsoluteErrorMinutes: 14 },
    exponentialSmoothing: { maeMinutes: 14, rmseMinutes: 18, medianAbsoluteErrorMinutes: 12 },
    intervalCoveragePercent: 90, minimumPredictionsForReporting: 10,
    methodology: "Rolling-origin backtest; each prediction uses only earlier observations",
  }}));
  await page.route("**/api/recommendations?**", route => route.fulfill({ json: [{
    hospital: { id: "h1", name: "Toronto General Hospital", waitMinutes: 80,
      predictedWaitMinutes: 72, predictionLowerMinutes: 60, predictionUpperMinutes: 84,
      forecastSampleCount: 12, forecastModel: "LINEAR_REGRESSION_V1", travelMinutes: 9,
      travelDistanceKm: 3.2, travelSource: "OSRM_ROUTE", latitude: 43.6596,
      longitude: -79.3889, observedAt: "2026-10-17T12:00:00Z" },
    score: 81, scoreBreakdown: { predictedWaitMinutes: 72, travelMinutes: 9, freshnessPenaltyMinutes: 0 },
    explanation: "9 min travel + 72 min predicted wait",
  }]}));

  await page.goto("/");
  await expect(page.getByText("Personalized ranking is active")).toBeVisible();
  await expect(page.getByText("Toronto General Hospital")).toBeVisible();
  await expect(page.getByText("72m forecast")).toBeVisible();
  await expect(page.getByText("road route")).toBeVisible();
});
