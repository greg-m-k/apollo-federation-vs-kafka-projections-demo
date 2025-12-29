// @ts-check
const { test, expect } = require('@playwright/test');

const DASHBOARD_URL = 'http://localhost:3000';

test('capture latency display screenshots', async ({ page }) => {
  await page.goto(DASHBOARD_URL);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);

  // Screenshot before any queries
  await page.screenshot({ path: 'test-results/1-before-query.png', fullPage: true });
  console.log('Screenshot 1: Before any queries');

  // Click Federation query
  await page.getByText('Query Composed View').click();
  await expect(page.getByText(/Success:.*ms/)).toBeVisible({ timeout: 10000 });
  await page.waitForTimeout(500);

  // Screenshot after Federation query
  await page.screenshot({ path: 'test-results/2-after-federation-query.png', fullPage: true });
  console.log('Screenshot 2: After Federation query - check diagram for timing labels');

  // Click Kafka query
  await page.getByText('Query Local Projection').click();
  await page.waitForTimeout(2000);

  // Screenshot after both queries
  await page.screenshot({ path: 'test-results/3-after-both-queries.png', fullPage: true });
  console.log('Screenshot 3: After both queries');

  // Get the diagram SVG content to verify timing is there
  const federationPanel = page.locator('.architecture-panel').first();
  const federationSvg = federationPanel.locator('svg');
  const federationDiagramText = await federationSvg.textContent();
  console.log('\nFederation diagram text:', federationDiagramText?.substring(0, 500));

  const kafkaPanel = page.locator('.architecture-panel').nth(1);
  const kafkaSvg = kafkaPanel.locator('svg');
  const kafkaDiagramText = await kafkaSvg.textContent();
  console.log('\nKafka diagram text:', kafkaDiagramText?.substring(0, 500));

  // Verify timing appears
  expect(federationDiagramText).toMatch(/\d+ms/);
  expect(kafkaDiagramText).toMatch(/\d+ms/);
});
