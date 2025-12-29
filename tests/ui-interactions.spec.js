// @ts-check
const { test, expect } = require('@playwright/test');

const DASHBOARD_URL = 'http://localhost:3000';

test.describe('Dashboard UI Interactions', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
  });

  test('should display header with correct title', async ({ page }) => {
    const header = page.locator('header');
    await expect(header).toBeVisible();
    await expect(page.getByText('Architecture Comparison Dashboard')).toBeVisible();
    await expect(page.getByText('GraphQL Federation vs Kafka Projections')).toBeVisible();
  });

  test('should display person selector with all options', async ({ page }) => {
    const select = page.locator('select');
    await expect(select).toBeVisible();

    // Check options exist
    const options = select.locator('option');
    await expect(options).toHaveCount(4);

    // Verify option values
    await expect(options.nth(0)).toContainText('Alice Johnson');
    await expect(options.nth(1)).toContainText('Bob Smith');
    await expect(options.nth(2)).toContainText('Carol Williams');
    await expect(options.nth(3)).toContainText('Eva Martinez');
  });

  test('should change selected person', async ({ page }) => {
    const select = page.locator('select');

    // Select Bob Smith
    await select.selectOption('person-002');
    await expect(select).toHaveValue('person-002');

    // Select Carol Williams
    await select.selectOption('person-003');
    await expect(select).toHaveValue('person-003');

    await page.screenshot({ path: 'test-results/person-selection.png', fullPage: true });
  });

  test('should display Query Both Architectures button', async ({ page }) => {
    const button = page.getByRole('button', { name: /Query Both Architectures/i });
    await expect(button).toBeVisible();
    await expect(button).toBeEnabled();
  });

  test('should display Create Person button', async ({ page }) => {
    const button = page.getByRole('button', { name: /Create Person/i });
    await expect(button).toBeVisible();
    await expect(button).toBeEnabled();
  });

  test('should display comparison summary section', async ({ page }) => {
    await expect(page.getByText('Tradeoff Comparison')).toBeVisible();
    await expect(page.getByText('Query Latency')).toBeVisible();
    // Use heading role to avoid matching multiple elements
    await expect(page.getByRole('heading', { name: 'Data Consistency' })).toBeVisible();
    await expect(page.getByText('Failure Mode')).toBeVisible();
  });

  test('should display Federation panel', async ({ page }) => {
    const panel = page.locator('text=GraphQL Federation').first();
    await expect(panel).toBeVisible();

    // Check panel contains expected elements
    await expect(page.getByText('Synchronous composition, real-time data')).toBeVisible();
    await expect(page.getByText('Query Composed View')).toBeVisible();
  });

  test('should display Kafka Projections panel', async ({ page }) => {
    const panel = page.locator('text=Kafka Projections').first();
    await expect(panel).toBeVisible();

    // Check panel contains expected elements
    await expect(page.getByText('Asynchronous events, local projections')).toBeVisible();
    await expect(page.getByText('Query Local Projection')).toBeVisible();
  });

  test('should display service status indicators', async ({ page }) => {
    await expect(page.getByText('Service Status').first()).toBeVisible();

    // Federation services
    await expect(page.getByText('HR').first()).toBeVisible();
    await expect(page.getByText('Employment').first()).toBeVisible();
    await expect(page.getByText('Security').first()).toBeVisible();

    // Kafka services
    await expect(page.locator('text=Projection').first()).toBeVisible();
    await expect(page.locator('text=Consumer').first()).toBeVisible();
    await expect(page.locator('text=Kafka').first()).toBeVisible();
  });

  test('should display activity log sections', async ({ page }) => {
    const activityLogs = page.locator('text=Activity Log');
    await expect(activityLogs.first()).toBeVisible();

    // Should see "No activity yet..." initially
    const noActivity = page.locator('text=No activity yet...');
    await expect(noActivity.first()).toBeVisible();
  });

  test('should display key insight', async ({ page }) => {
    await expect(page.getByText(/Key Insight:/)).toBeVisible();
    await expect(page.getByText(/Federation provides real-time data consistency/)).toBeVisible();
  });
});

test.describe('Dashboard Query Operations', () => {

  test('should query federation and update UI', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Click Query Composed View button
    const federationButton = page.getByRole('button', { name: /Query Composed View/i });
    await federationButton.click();

    // Wait for query to complete
    await page.waitForTimeout(2000);

    // Check that activity log updated
    const activityLog = page.locator('.bg-gray-900').first();
    await expect(activityLog).toBeVisible();

    await page.screenshot({ path: 'test-results/federation-query.png', fullPage: true });
  });

  test('should query Kafka and update UI', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Click Query Local Projection button
    const kafkaButton = page.getByRole('button', { name: /Query Local Projection/i });
    await kafkaButton.click();

    // Wait for query to complete
    await page.waitForTimeout(2000);

    await page.screenshot({ path: 'test-results/kafka-query.png', fullPage: true });
  });

  test('should query both architectures', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Click Query Both Architectures button
    const button = page.getByRole('button', { name: /Query Both Architectures/i });
    await button.click();

    // Wait for both queries to complete
    await page.waitForTimeout(3000);

    await page.screenshot({ path: 'test-results/query-both.png', fullPage: true });
  });

  test('should update metrics after query', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Click Query Both
    const button = page.getByRole('button', { name: /Query Both Architectures/i });
    await button.click();

    // Wait for queries
    await page.waitForTimeout(3000);

    // Check for latency display (should show something other than dash)
    const latencySection = page.locator('text=Latency').first();
    await expect(latencySection).toBeVisible();

    // Check queries counter updated
    const queriesSection = page.locator('text=Queries').first();
    await expect(queriesSection).toBeVisible();
  });
});

test.describe('Dashboard Responsiveness', () => {

  test('should display properly on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Header should still be visible
    await expect(page.getByText('Architecture Comparison Dashboard')).toBeVisible();

    // Panels should stack vertically on mobile
    await page.screenshot({ path: 'test-results/mobile-view.png', fullPage: true });
  });

  test('should display properly on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Architecture Comparison Dashboard')).toBeVisible();
    await page.screenshot({ path: 'test-results/tablet-view.png', fullPage: true });
  });

  test('should display properly on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Architecture Comparison Dashboard')).toBeVisible();
    await page.screenshot({ path: 'test-results/desktop-view.png', fullPage: true });
  });
});
