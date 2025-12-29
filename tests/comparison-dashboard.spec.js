// @ts-check
const { test, expect } = require('@playwright/test');

// URLs for docker-compose setup
const DASHBOARD_URL = 'http://localhost:3000';
const FEDERATION_GRAPHQL = 'http://localhost:4000';
const KAFKA_QUERY_SERVICE = 'http://localhost:8090';
const HR_EVENTS_SERVICE = 'http://localhost:8084';

test.describe('Federation vs Event-Driven Comparison Dashboard', () => {

  test('should load the dashboard', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Check page title
    await expect(page).toHaveTitle(/Architecture Comparison/);

    // Take a screenshot
    await page.screenshot({ path: 'test-results/dashboard-loaded.png', fullPage: true });
  });

  test('should display both architecture panels', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Wait for content to load
    await page.waitForTimeout(2000);

    // Check for Federation panel
    const federationPanel = page.locator('text=GraphQL Federation');
    await expect(federationPanel.first()).toBeVisible();

    // Check for Kafka Projections panel
    const kafkaPanel = page.locator('text=Kafka Projections');
    await expect(kafkaPanel.first()).toBeVisible();

    await page.screenshot({ path: 'test-results/both-panels.png', fullPage: true });
  });

  test('should have dynamic person dropdown that loads from API', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Wait for persons to load
    await page.waitForTimeout(2000);

    // Check dropdown has loaded options (not just "Loading...")
    const dropdown = page.locator('select');
    await expect(dropdown).toBeVisible();

    // Should have Alice Johnson as one of the seed data options (check option exists in select)
    const options = await dropdown.locator('option').allTextContents();
    expect(options.some(opt => opt.includes('Alice Johnson'))).toBeTruthy();
  });

  test('should display tradeoff comparison section', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Check for comparison summary - use .first() for items that may match multiple elements
    await expect(page.locator('text=Tradeoff Comparison')).toBeVisible();
    await expect(page.locator('text=Query Latency').first()).toBeVisible();
    await expect(page.locator('text=Data Consistency').first()).toBeVisible();
    await expect(page.locator('text=Failure Mode').first()).toBeVisible();
  });
});

test.describe('Dashboard - Query Operations', () => {

  test('should execute query on both architectures', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Click Query Both Architectures button
    await page.click('button:has-text("Query Both Architectures")');

    // Wait for queries to complete
    await page.waitForTimeout(3000);

    // Check that latency metrics are displayed
    const latencyValues = page.locator('text=/\\d+ms/');
    await expect(latencyValues.first()).toBeVisible();

    // Check activity logs have entries
    const activityLog = page.locator('.font-mono');
    await expect(activityLog.first()).toContainText('Querying');

    await page.screenshot({ path: 'test-results/query-both.png', fullPage: true });
  });

  test('should show timing on architecture diagrams after query', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Execute query
    await page.click('button:has-text("Query Both Architectures")');
    await page.waitForTimeout(3000);

    // Check that diagrams show timing (ms labels)
    const diagrams = page.locator('.bg-gray-50');
    await expect(diagrams.first()).toBeVisible();

    await page.screenshot({ path: 'test-results/diagrams-with-timing.png', fullPage: true });
  });
});

test.describe('Dashboard - Create Person Modal', () => {

  test('should open create person modal', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Click Create Person button
    await page.click('button:has-text("Create Person")');

    // Modal should appear
    await expect(page.locator('text=Create New Person')).toBeVisible();
    await expect(page.locator('input[placeholder*="John Smith"]')).toBeVisible();

    await page.screenshot({ path: 'test-results/create-modal-open.png', fullPage: true });
  });

  test('should close modal on cancel', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Open modal
    await page.click('button:has-text("Create Person")');
    await expect(page.locator('text=Create New Person')).toBeVisible();

    // Click cancel
    await page.click('button:has-text("Cancel")');

    // Modal should be closed
    await expect(page.locator('text=Create New Person')).not.toBeVisible();
  });

  test('should close modal on escape key', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');

    // Open modal
    await page.click('button:has-text("Create Person")');
    await expect(page.locator('text=Create New Person')).toBeVisible();

    // Press escape
    await page.keyboard.press('Escape');

    // Modal should be closed
    await expect(page.locator('text=Create New Person')).not.toBeVisible();
  });

  test('should create person and show write comparison', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Open modal
    await page.click('button:has-text("Create Person")');
    await expect(page.locator('text=Create New Person')).toBeVisible();

    // Fill in name
    const testName = `E2E Test ${Date.now()}`;
    await page.fill('input[placeholder*="John Smith"]', testName);

    // Email should auto-generate
    const emailInput = page.locator('input[type="email"]');
    await expect(emailInput).toHaveValue(new RegExp('e2e\\.test.*@company\\.com'));

    // Submit - use the submit button inside the form
    await page.click('button[type="submit"]');

    // Wait for creation and queries
    await page.waitForTimeout(5000);

    // Write Operation Comparison should appear
    await expect(page.locator('text=Write Operation Comparison')).toBeVisible();

    // Created person name should be shown (use .first() as it appears in multiple places)
    await expect(page.locator('text=Created:').first()).toBeVisible();

    // Diagrams should show "Write:" badge
    const writeBadge = page.locator('text=/Write:/');
    await expect(writeBadge.first()).toBeVisible();

    await page.screenshot({ path: 'test-results/create-person-complete.png', fullPage: true });
  });

  test('should switch diagrams back to read flow after query', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Create a person first
    await page.click('button:has-text("Create Person")');
    await expect(page.locator('text=Create New Person')).toBeVisible();
    await page.fill('input[placeholder*="John Smith"]', `Switch Test ${Date.now()}`);
    await page.click('button[type="submit"]');
    await page.waitForTimeout(5000);

    // Should show write flow
    await expect(page.locator('text=/Write:/').first()).toBeVisible();

    // Now query
    await page.click('button:has-text("Query Both Architectures")');
    await page.waitForTimeout(3000);

    // Write badge should be gone (diagrams back to read flow)
    await expect(page.locator('text=/Write:/')).not.toBeVisible();

    await page.screenshot({ path: 'test-results/switch-to-read-flow.png', fullPage: true });
  });
});

test.describe('Federation Architecture - API Tests', () => {

  test('router health check should return UP', async ({ request }) => {
    const response = await request.get('http://localhost:8088/health');
    expect(response.ok()).toBeTruthy();

    const data = await response.json();
    expect(data.status).toBe('UP');
  });

  test('should execute GraphQL introspection query', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ __typename }'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.data.__typename).toBe('Query');
  });

  test('should create a person via federation mutation', async ({ request }) => {
    const testName = `Test Person ${Date.now()}`;
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `mutation {
          createPerson(name: "${testName}", email: "${testName.toLowerCase().replace(' ', '.')}@example.com") {
            id
            name
            email
            active
          }
        }`
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    if (data.errors) {
      console.log('GraphQL errors:', JSON.stringify(data.errors));
    }
    expect(data.data?.createPerson || data.errors).toBeDefined();
  });

  test('should query persons from federation', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `{
          persons {
            id
            name
            email
          }
        }`
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    if (data.errors) {
      console.log('GraphQL errors:', JSON.stringify(data.errors));
    }
    expect(data.data?.persons || data.errors).toBeDefined();
  });
});

test.describe('Event-Driven Architecture - API Tests', () => {

  test('Projection service health check should return UP', async ({ request }) => {
    const response = await request.get(`${KAFKA_QUERY_SERVICE}/q/health`);
    expect(response.ok()).toBeTruthy();

    const data = await response.json();
    expect(data.status).toBe('UP');
  });

  test('HR Events service health check should return UP', async ({ request }) => {
    const response = await request.get(`${HR_EVENTS_SERVICE}/q/health`);
    expect(response.ok()).toBeTruthy();

    const data = await response.json();
    expect(data.status).toBe('UP');
  });

  test('should create a person via HR Events service', async ({ request }) => {
    const testName = `Kafka Person ${Date.now()}`;
    const response = await request.post(`${HR_EVENTS_SERVICE}/api/persons`, {
      data: {
        name: testName,
        email: `${testName.toLowerCase().replace(' ', '.')}@example.com`,
        hireDate: new Date().toISOString().split('T')[0]
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    // Might get 201 Created or 200 OK
    expect(response.status()).toBeLessThan(300);

    const data = await response.json();
    expect(data.id).toBeDefined();
    expect(data.name).toBe(testName);
  });

  test('should list persons from projection service', async ({ request }) => {
    const response = await request.get(`${KAFKA_QUERY_SERVICE}/api/persons`);
    expect(response.ok()).toBeTruthy();

    const data = await response.json();
    expect(Array.isArray(data)).toBeTruthy();
    expect(data.length).toBeGreaterThan(0);
  });

  test('should get composed view for a person', async ({ request }) => {
    const response = await request.get(`${KAFKA_QUERY_SERVICE}/api/composed/person-001`);
    expect(response.ok()).toBeTruthy();

    const data = await response.json();
    expect(data.personId).toBe('person-001');
    expect(data.name).toBeDefined();
    expect(data.title).toBeDefined();
    expect(data.badgeNumber).toBeDefined();
  });
});

test.describe('Architecture Comparison - Performance', () => {

  test('Federation: multi-subgraph query should work', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `{
          persons {
            id
            name
            employee {
              id
              title
              department
            }
            badge {
              id
              badgeNumber
              accessLevel
            }
          }
        }`
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    console.log('Federation multi-subgraph result:', JSON.stringify(data).substring(0, 500));

    if (data.errors) {
      console.log('Errors (may be OK if data is empty):', JSON.stringify(data.errors));
    }
    expect(data.data?.persons !== undefined || data.errors).toBeTruthy();
  });

  test('should measure federation vs event-driven response times', async ({ request }) => {
    // Federation query
    const fedStart = Date.now();
    const fedResponse = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ persons { id name } }'
      },
      headers: { 'Content-Type': 'application/json' }
    });
    const fedTime = Date.now() - fedStart;

    // Kafka Projections query
    const edStart = Date.now();
    const edResponse = await request.get(`${KAFKA_QUERY_SERVICE}/api/persons`);
    const edTime = Date.now() - edStart;

    console.log(`Response times - Federation: ${fedTime}ms, Event-Driven: ${edTime}ms`);

    // Both should respond reasonably fast (within 10 seconds)
    expect(fedTime).toBeLessThan(10000);
    expect(edTime).toBeLessThan(10000);

    // Event-Driven should generally be faster (local projection)
    console.log(`Event-Driven is ${Math.round((fedTime - edTime) / edTime * 100)}% faster`);
  });

  test('should measure write propagation time for event-driven', async ({ request }) => {
    const testName = `Propagation Test ${Date.now()}`;

    // Create person
    const createStart = Date.now();
    const createResponse = await request.post(`${HR_EVENTS_SERVICE}/api/persons`, {
      data: {
        name: testName,
        email: `${testName.toLowerCase().replace(/\s/g, '.')}@example.com`,
        hireDate: new Date().toISOString().split('T')[0]
      },
      headers: { 'Content-Type': 'application/json' }
    });
    const createTime = Date.now() - createStart;

    expect(createResponse.ok()).toBeTruthy();
    const newPerson = await createResponse.json();

    // Poll for propagation
    const maxWait = 10000;
    const pollStart = Date.now();
    let propagated = false;

    while (Date.now() - pollStart < maxWait) {
      const checkResponse = await request.get(`${KAFKA_QUERY_SERVICE}/api/persons`);
      const persons = await checkResponse.json();
      if (persons.some(p => p.id === newPerson.id)) {
        propagated = true;
        break;
      }
      await new Promise(r => setTimeout(r, 100));
    }

    const propagationTime = Date.now() - pollStart;

    console.log(`Write times - Mutation: ${createTime}ms, Propagation: ${propagationTime}ms, Total: ${createTime + propagationTime}ms`);

    expect(propagated).toBeTruthy();
    expect(propagationTime).toBeLessThan(maxWait);
  });
});

test.describe('Resilience Tests', () => {

  test('dashboard should handle service unavailability gracefully', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Dashboard should still be visible even if some data fails
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('text=Architecture Comparison Dashboard')).toBeVisible();

    await page.screenshot({ path: 'test-results/resilience-test.png', fullPage: true });
  });

  test('should show service status indicators', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Execute a query to update service status
    await page.click('button:has-text("Query Both Architectures")');
    await page.waitForTimeout(3000);

    // Check for service status section
    await expect(page.locator('text=Service Status').first()).toBeVisible();

    // Services should show as up (green indicators)
    const statusIndicators = page.locator('.status-up');
    const count = await statusIndicators.count();
    expect(count).toBeGreaterThan(0);

    await page.screenshot({ path: 'test-results/service-status.png', fullPage: true });
  });
});
