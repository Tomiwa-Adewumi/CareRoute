import { expect, test, type Page } from "@playwright/test";

function recommendation(overrides = {}) {
  return {
    hospital: { id: "h1", name: "Toronto General Hospital", waitMinutes: 80,
      forecastModel: "LATEST_WAIT_BASELINE", travelMinutes: 9, travelDistanceKm: 3.2,
      travelSource: "OSRM_ROUTE", observedAt: new Date().toISOString(), timestampSource: "ER_COMPASS_OBSERVED" },
    status: "AVAILABLE", score: 89,
    scoreBreakdown: { predictedWaitMinutes: 80, travelMinutes: 9, freshnessPenaltyMinutes: 0 },
    explanation: "80 min reported wait + 9 min travel", ...overrides,
  };
}
const evaluation = {
  status: "EVALUATED", evaluatedPredictions: 20, storedObservations: 48,
  linearRegression: { maeMinutes: 12, rmseMinutes: 15, medianAbsoluteErrorMinutes: 10 },
  latestWaitBaseline: { maeMinutes: 8, rmseMinutes: 12, medianAbsoluteErrorMinutes: 6 },
  movingAverageBaseline: { maeMinutes: 16, rmseMinutes: 20, medianAbsoluteErrorMinutes: 14 },
  exponentialSmoothing: { maeMinutes: 14, rmseMinutes: 18, medianAbsoluteErrorMinutes: 12 },
  intervalCoveragePercent: 90, minimumPredictionsForReporting: 10,
  methodology: "Exploratory rolling-origin backtest with verified timestamps",
};
async function metrics(page: Page, data = evaluation) {
  await page.route("**/api/model/metrics", route => route.fulfill({ json: data }));
}

test("location is opt-in and personalized results use the baseline", async ({ browser }) => {
  const context = await browser.newContext({ permissions: ["geolocation"], geolocation: { latitude: 43.6532, longitude: -79.3832 } });
  const page = await context.newPage();
  const queries: string[] = [];
  await metrics(page);
  await page.route("**/api/recommendations**", route => {
    queries.push(new URL(route.request().url()).search);
    return route.fulfill({ json: [recommendation()] });
  });
  await page.goto("/");
  await expect(page.getByText("80m reported")).toBeVisible();
  expect(queries.every(query => query === "")).toBe(true);
  await page.getByRole("button", { name: "Enable location for travel estimates" }).click();
  await expect(page.getByText("Personalized comparison is active", { exact: false })).toBeVisible();
  await expect(page.getByText("road route", { exact: false })).toBeVisible();
  expect(queries.some(query => query.includes("lat=43.6532"))).toBe(true);
  await page.getByRole("button", { name: "Disable location" }).click();
  await expect(page.getByText("Location is not active", { exact: false })).toBeVisible();
  await expect(page.getByText("80m reported")).toBeVisible();
  expect(queries.at(-1)).toBe("");
  await context.close();
});

test("unavailable and expired facilities have no ranking or score", async ({ page }) => {
  await metrics(page);
  const unavailable = recommendation({ status: "UNAVAILABLE", score: null, scoreBreakdown: null, explanation: "Travel estimate is unavailable" });
  const expired = recommendation();
  expired.hospital = { ...expired.hospital, id: "old", name: "Old Hospital", observedAt: new Date(Date.now() - 86400000).toISOString() };
  await page.route("**/api/recommendations**", route => route.fulfill({ json: [unavailable, expired] }));
  await page.goto("/");
  await expect(page.getByRole("region", { name: "Unavailable facilities" })).toBeVisible();
  await expect(page.getByText("No score", { exact: true })).toHaveCount(2);
  await expect(page.getByText("comparison score", { exact: true })).toHaveCount(0);
});

test("failed refresh clears prior rankings and a retry recovers", async ({ page }) => {
  await metrics(page);
  let fail = false;
  await page.route("**/api/recommendations**", route => fail
    ? route.fulfill({ status: 503, body: "Unavailable" }) : route.fulfill({ json: [recommendation()] }));
  await page.goto("/");
  await expect(page.getByText("80m reported")).toBeVisible();
  fail = true;
  await page.getByRole("button", { name: "Refresh data" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Could not refresh current data" })).toContainText("Previous rankings have been removed");
  await expect(page.getByText("80m reported")).toHaveCount(0);
  fail = false;
  await page.getByRole("button", { name: "Refresh data" }).click();
  await expect(page.getByText("80m reported")).toBeVisible();
});

test("automatic and focus refreshes request current data", async ({ page }) => {
  await page.clock.install();
  await metrics(page);
  let count = 0;
  await page.route("**/api/recommendations**", route => { count++; return route.fulfill({ json: [recommendation()] }); });
  await page.goto("/");
  await expect(page.getByText("80m reported")).toBeVisible();
  const initial = count;
  await page.clock.fastForward(61000);
  await expect.poll(() => count).toBeGreaterThan(initial);
  const afterTimer = count;
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect.poll(() => count).toBeGreaterThan(afterTimer);
});

test("small-sample metrics are withheld even if returned by an older API", async ({ page }) => {
  await metrics(page, { ...evaluation, status: "COLLECTING_HISTORY", evaluatedPredictions: 1 });
  await page.route("**/api/recommendations**", route => route.fulfill({ json: [] }));
  await page.goto("/");
  await expect(page.getByText("COLLECTING HISTORY", { exact: true })).toBeVisible();
  await expect(page.getByText("12m", { exact: true })).toHaveCount(0);
  await expect(page.getByText("90%", { exact: true })).toHaveCount(0);
});

test("denied location retains a usable non-personalized comparison", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "geolocation", { value: {
      getCurrentPosition: (_success: unknown, error: (error: { code: number }) => void) => error({ code: 1 }),
    } });
  });
  await metrics(page);
  await page.route("**/api/recommendations**", route => route.fulfill({ json: [recommendation()] }));
  await page.goto("/");
  await page.getByRole("button", { name: "Enable location for travel estimates" }).click();
  await expect(page.getByRole("button", { name: "Location not shared · travel excluded" })).toBeVisible();
  await expect(page.getByText("80m reported")).toBeVisible();
});
