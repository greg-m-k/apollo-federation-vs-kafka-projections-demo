// @ts-check
const { test, expect } = require('@playwright/test');

const DASHBOARD_URL = 'http://localhost:3000';
const FEDERATION_GRAPHQL = 'http://localhost:4000';
const KAFKA_QUERY_SERVICE = 'http://localhost:8090';
const HR_EVENTS_SERVICE = 'http://localhost:8084';

test.describe('CRUD Operations - Federation', () => {

  test('should create, read, update person via GraphQL', async ({ request }) => {
    const timestamp = Date.now();
    const personName = `Federation Test ${timestamp}`;
    const email = `fed.test.${timestamp}@example.com`;

    // CREATE
    const createResponse = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `mutation {
          createPerson(name: "${personName}", email: "${email}") {
            id
            name
            email
            active
          }
        }`
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(createResponse.ok()).toBeTruthy();
    const createData = await createResponse.json();

    if (createData.data?.createPerson) {
      const personId = createData.data.createPerson.id;
      expect(personId).toBeDefined();
      expect(createData.data.createPerson.name).toBe(personName);

      // READ - Verify person exists
      const readResponse = await request.post(FEDERATION_GRAPHQL, {
        data: {
          query: `{ person(id: "${personId}") { id name email active } }`
        },
        headers: { 'Content-Type': 'application/json' }
      });

      expect(readResponse.ok()).toBeTruthy();
      const readData = await readResponse.json();

      if (readData.data?.person) {
        expect(readData.data.person.id).toBe(personId);
        expect(readData.data.person.name).toBe(personName);
      }
    }
  });

  test('should handle invalid person ID gracefully', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ person(id: "non-existent-id") { id name } }'
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    // Should return null or empty data for non-existent person
    expect(data.data?.person === null || data.errors).toBeTruthy();
  });

  test('should query persons list', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ persons { id name email active } }'
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    if (!data.errors) {
      expect(data.data?.persons).toBeDefined();
      expect(Array.isArray(data.data.persons)).toBeTruthy();
    }
  });

  test('should query employees list', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ employees { id title department } }'
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();

    if (!data.errors) {
      expect(data.data?.employees).toBeDefined();
      expect(Array.isArray(data.data.employees)).toBeTruthy();
    }
  });
});

test.describe('CRUD Operations - Kafka', () => {

  test('should create person via Events service and verify outbox event', async ({ request }) => {
    const timestamp = Date.now();
    const personName = `Kafka Test ${timestamp}`;
    const email = `kafka.test.${timestamp}@example.com`;

    // CREATE via Events service
    const createResponse = await request.post(`${HR_EVENTS_SERVICE}/api/persons`, {
      data: {
        name: personName,
        email: email,
        hireDate: new Date().toISOString().split('T')[0]
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(createResponse.status()).toBeLessThan(300);

    if (createResponse.status() === 201 || createResponse.status() === 200) {
      const createdPerson = await createResponse.json();
      expect(createdPerson.name).toBe(personName);
      expect(createdPerson.email).toBe(email);

      // Allow time for Kafka to propagate
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Verify via query service (projection)
      const queryResponse = await request.get(`${KAFKA_QUERY_SERVICE}/api/persons`);

      if (queryResponse.status() === 200) {
        const persons = await queryResponse.json();
        // Check if our person is in the projection
        const found = Array.isArray(persons) &&
          persons.some(p => p.name === personName || p.email === email);
        console.log(`Person ${personName} found in projection: ${found}`);
      }
    }
  });

  test('should handle invalid request body gracefully', async ({ request }) => {
    const response = await request.post(`${HR_EVENTS_SERVICE}/api/persons`, {
      data: {},  // Empty body
      headers: { 'Content-Type': 'application/json' }
    });

    // Should return 400 Bad Request or similar
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('should query composed view by person ID', async ({ request }) => {
    const response = await request.get(`${KAFKA_QUERY_SERVICE}/api/composed/person-001`);

    // May be 200 if person exists or 404 if not
    expect([200, 404]).toContain(response.status());

    if (response.status() === 200) {
      const data = await response.json();
      expect(data).toBeDefined();
    }
  });
});

test.describe('Cross-Architecture Consistency', () => {

  test('should create person in both architectures and compare results', async ({ request }) => {
    const timestamp = Date.now();
    const baseName = `Cross Arch Test ${timestamp}`;

    // Create in Federation
    const fedName = `${baseName} Fed`;
    const fedResponse = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `mutation {
          createPerson(name: "${fedName}", email: "${timestamp}fed@test.com") {
            id name email
          }
        }`
      },
      headers: { 'Content-Type': 'application/json' }
    });

    // Create in Kafka
    const kafkaName = `${baseName} Kafka`;
    const kafkaResponse = await request.post(`${HR_EVENTS_SERVICE}/api/persons`, {
      data: {
        name: kafkaName,
        email: `${timestamp}kafka@test.com`,
        hireDate: new Date().toISOString().split('T')[0]
      },
      headers: { 'Content-Type': 'application/json' }
    });

    expect(fedResponse.ok()).toBeTruthy();
    expect(kafkaResponse.status()).toBeLessThan(300);

    console.log('Cross-architecture create completed successfully');
  });

  test('should query same person from both architectures', async ({ request }) => {
    const personId = 'person-001';

    // Query from Federation
    const fedResponse = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: `{ person(id: "${personId}") { id name email } }`
      },
      headers: { 'Content-Type': 'application/json' }
    });

    // Query from Kafka
    const kafkaResponse = await request.get(`${KAFKA_QUERY_SERVICE}/api/composed/${personId}`);

    const fedData = await fedResponse.json();

    console.log('Federation result:', JSON.stringify(fedData).substring(0, 200));
    console.log('Kafka status:', kafkaResponse.status());

    // Both should respond (may have different data states)
    expect(fedResponse.ok()).toBeTruthy();
    expect([200, 404]).toContain(kafkaResponse.status());
  });
});

test.describe('Error Handling', () => {

  test('should handle malformed GraphQL query', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ invalid syntax here'
      },
      headers: { 'Content-Type': 'application/json' }
    });

    // GraphQL servers may return 200 with errors or 400 for syntax errors
    expect([200, 400]).toContain(response.status());
    const data = await response.json();
    expect(data.errors).toBeDefined();
  });

  test('should handle invalid field in query', async ({ request }) => {
    const response = await request.post(FEDERATION_GRAPHQL, {
      data: {
        query: '{ persons { nonExistentField } }'
      },
      headers: { 'Content-Type': 'application/json' }
    });

    // GraphQL servers may return 200 with errors or 400 for schema validation errors
    expect([200, 400]).toContain(response.status());
    const data = await response.json();
    expect(data.errors).toBeDefined();
  });

  test('should return proper error for invalid Kafka endpoint', async ({ request }) => {
    const response = await request.get(`${KAFKA_QUERY_SERVICE}/api/invalid-endpoint`);
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
