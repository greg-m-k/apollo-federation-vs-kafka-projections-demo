const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1400, height: 900 });

  await page.goto('http://localhost:3000');
  await page.waitForTimeout(3000);

  // Screenshot 1: Initial state
  await page.screenshot({ path: 'screenshot-1-initial.png', fullPage: true });
  console.log('Saved: screenshot-1-initial.png');

  // Create a person
  await page.click('button:has-text("Create Person")');
  await page.waitForTimeout(500);
  await page.fill('input[placeholder="e.g. John Smith"]', 'Test Person ' + Date.now());
  await page.waitForTimeout(300);
  await page.locator('button[type="submit"]:has-text("Create Person")').click();
  await page.waitForTimeout(6000);

  // Screenshot 2: After create
  await page.screenshot({ path: 'screenshot-2-after-create.png', fullPage: true });
  console.log('Saved: screenshot-2-after-create.png');

  // Click Query Both
  await page.click('text=Query Both');
  await page.waitForTimeout(3000);

  // Screenshot 3: After Query Both
  await page.screenshot({ path: 'screenshot-3-after-query.png', fullPage: true });
  console.log('Saved: screenshot-3-after-query.png');

  await browser.close();
  console.log('Done! Check the screenshots.');
})();
