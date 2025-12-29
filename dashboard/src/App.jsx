import React, { useState, useEffect, useCallback } from 'react';
import ArchitecturePanel from './components/ArchitecturePanel';
import ComparisonSummary from './components/ComparisonSummary';
import CreatePersonModal from './components/CreatePersonModal';

const FEDERATION_URL = window.location.hostname === 'localhost'
  ? 'http://localhost:4000'
  : '/api/federation';

const KAFKA_URL = window.location.hostname === 'localhost'
  ? 'http://localhost:8090/api'
  : '/api/kafka';

// HR Events service for mutations (separate from query service)
const HR_EVENTS_URL = window.location.hostname === 'localhost'
  ? 'http://localhost:8084'
  : '/api/hr-events';

function App() {
  const [federationMetrics, setFederationMetrics] = useState({
    latency: null,
    stageTiming: { router: null, hr: null, employment: null, security: null },
    servicesUp: { hr: false, employment: false, security: false },
    lastQuery: null,
    queryCount: 0,
    errorCount: 0
  });

  const [kafkaMetrics, setKafkaMetrics] = useState({
    latency: null,
    stageTiming: { queryService: null },
    servicesUp: { query: false, consumer: false, kafka: false },
    lastQuery: null,
    queryCount: 0,
    dataFreshness: 'N/A',
    consumerLag: 0
  });

  // Mutation metrics - comparing write performance
  const [mutationMetrics, setMutationMetrics] = useState({
    federation: { mutationTime: null, totalTime: null, personName: null, personId: null },
    eventDriven: { mutationTime: null, propagationTime: null, totalTime: null, personName: null, personId: null }
  });

  // Estimate Federation stage timing based on total latency
  // In reality, the router makes parallel calls to subgraphs, so this is an approximation
  // Breakdown: Client→Router (network), Router processing, Router→Subgraph (network), Subgraph→DB (query)
  const estimateFederationTiming = (totalLatency) => {
    const networkOverhead = Math.round(totalLatency * 0.10); // ~10% network between client and router
    const routerProcessing = Math.round(totalLatency * 0.08); // ~8% router parse/plan/merge
    const subgraphTime = totalLatency - networkOverhead - routerProcessing;
    // Each subgraph: ~30% network, ~70% DB query
    return {
      network: networkOverhead,
      router: routerProcessing,
      hr: Math.round(subgraphTime * 0.35),
      hrDb: Math.round(subgraphTime * 0.35 * 0.70),
      employment: Math.round(subgraphTime * 0.35),
      employmentDb: Math.round(subgraphTime * 0.35 * 0.70),
      security: Math.round(subgraphTime * 0.30),
      securityDb: Math.round(subgraphTime * 0.30 * 0.70)
    };
  };

  const [logs, setLogs] = useState({ federation: [], kafka: [] });
  const [selectedPerson, setSelectedPerson] = useState('person-001');
  const [availablePersons, setAvailablePersons] = useState([]);
  const [loadingPersons, setLoadingPersons] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Fetch available persons from the projection service
  const fetchPersons = useCallback(async () => {
    try {
      const response = await fetch(`${KAFKA_URL}/persons`);
      const persons = await response.json();
      // Sort by name, with original seed data first
      const sorted = persons.sort((a, b) => {
        // Seed data (person-001 to person-005) first
        const aIsSeed = a.id.match(/^person-00[1-5]$/);
        const bIsSeed = b.id.match(/^person-00[1-5]$/);
        if (aIsSeed && !bIsSeed) return -1;
        if (!aIsSeed && bIsSeed) return 1;
        return a.name.localeCompare(b.name);
      });
      setAvailablePersons(sorted);
    } catch (e) {
      console.error('Failed to fetch persons:', e);
    } finally {
      setLoadingPersons(false);
    }
  }, []);

  // Load persons on mount
  useEffect(() => {
    fetchPersons();
  }, [fetchPersons]);

  // Health check on mount to update service status indicators
  useEffect(() => {
    const checkHealth = async () => {
      // Check Federation (simple GraphQL query to router)
      try {
        const fedResponse = await fetch(FEDERATION_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: '{ __typename }' })
        });
        if (fedResponse.ok) {
          setFederationMetrics(prev => ({
            ...prev,
            servicesUp: { hr: true, employment: true, security: true }
          }));
        }
      } catch (e) {
        // Federation services not available
      }

      // Check Kafka (query service health - base URL without /api)
      const kafkaBaseUrl = KAFKA_URL.replace('/api', '');
      try {
        const kafkaResponse = await fetch(`${kafkaBaseUrl}/q/health/ready`, { method: 'GET' });
        if (kafkaResponse.ok) {
          setKafkaMetrics(prev => ({
            ...prev,
            servicesUp: { query: true, consumer: true, kafka: true }
          }));
        }
      } catch (e) {
        // Kafka services not available
      }
    };

    checkHealth();
  }, []);

  const addLog = useCallback((side, message) => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs(prev => ({
      ...prev,
      [side]: [...prev[side].slice(-20), { timestamp, message }]
    }));
  }, []);

  // Clear mutation metrics to switch diagrams back to read flow
  const clearMutationMetrics = useCallback(() => {
    setMutationMetrics({
      federation: { mutationTime: null, totalTime: null, personName: null, personId: null },
      eventDriven: { mutationTime: null, propagationTime: null, totalTime: null, personName: null, personId: null }
    });
  }, []);

  // Query Federation (GraphQL)
  const queryFederation = useCallback(async (personIdOverride = null) => {
    // Ignore event objects passed from button clicks
    const validOverride = typeof personIdOverride === 'string' ? personIdOverride : null;
    if (!validOverride) clearMutationMetrics(); // Only clear if not querying just-created person
    const personId = validOverride || selectedPerson;
    const startTime = performance.now();
    addLog('federation', `Querying for ${personId}...`);

    try {
      // Using inline ID instead of variables due to SmallRye GraphQL type handling
      const query = `
        {
          person(id: "${personId}") {
            id
            name
            email
            hireDate
            active
            employee {
              id
              title
              department
              salary
            }
            badge {
              id
              badgeNumber
              accessLevel
              clearance
            }
          }
        }
      `;

      const response = await fetch(FEDERATION_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query })
      });

      const data = await response.json();
      const latency = Math.round(performance.now() - startTime);
      const stageTiming = estimateFederationTiming(latency);

      setFederationMetrics(prev => ({
        ...prev,
        latency,
        stageTiming,
        lastQuery: data,
        queryCount: prev.queryCount + 1,
        servicesUp: { hr: true, employment: true, security: true }
      }));

      addLog('federation', `Success: ${latency}ms (Router: ${stageTiming.router}ms, HR: ${stageTiming.hr}ms, Emp: ${stageTiming.employment}ms, Sec: ${stageTiming.security}ms)`);
    } catch (error) {
      setFederationMetrics(prev => ({
        ...prev,
        errorCount: prev.errorCount + 1
      }));
      addLog('federation', `ERROR: ${error.message}`);
    }
  }, [selectedPerson, addLog, clearMutationMetrics]);

  // Query Kafka Projections (Local Projections)
  const queryKafka = useCallback(async (personIdOverride = null) => {
    // Ignore event objects passed from button clicks
    const validOverride = typeof personIdOverride === 'string' ? personIdOverride : null;
    if (!validOverride) clearMutationMetrics(); // Only clear if not querying just-created person
    const personId = validOverride || selectedPerson;
    const startTime = performance.now();
    addLog('kafka', `Querying for ${personId}...`);

    try {
      const response = await fetch(`${KAFKA_URL}/composed/${personId}`);
      const data = await response.json();
      const latency = Math.round(performance.now() - startTime);

      // Get backend query time from header if available
      const backendTime = response.headers.get('X-Query-Time-Ms');
      const queryServiceTime = backendTime ? parseInt(backendTime) : Math.round(latency * 0.3);

      setKafkaMetrics(prev => ({
        ...prev,
        latency,
        stageTiming: { queryService: queryServiceTime },
        lastQuery: data,
        queryCount: prev.queryCount + 1,
        servicesUp: { query: true, consumer: true, kafka: true },
        dataFreshness: data.freshness?.dataFreshness || 'N/A'
      }));

      addLog('kafka', `Success: ${latency}ms (Projection Service: ${queryServiceTime}ms, freshness: ${data.freshness?.dataFreshness})`);
    } catch (error) {
      setKafkaMetrics(prev => ({
        ...prev
      }));
      addLog('kafka', `ERROR: ${error.message}`);
    }
  }, [selectedPerson, addLog, clearMutationMetrics]);

  // Query both sides
  const queryBoth = useCallback(() => {
    queryFederation();
    queryKafka();
  }, [queryFederation, queryKafka]);

  // Poll for person to appear in projection (measures propagation delay)
  const waitForPropagation = useCallback(async (personId, maxWaitMs = 10000) => {
    const startTime = performance.now();
    const pollInterval = 100; // Check every 100ms

    while (performance.now() - startTime < maxWaitMs) {
      try {
        const response = await fetch(`${KAFKA_URL}/persons`);
        const persons = await response.json();
        if (persons.some(p => p.id === personId)) {
          return Math.round(performance.now() - startTime);
        }
      } catch (e) {
        // Ignore errors during polling
      }
      await new Promise(resolve => setTimeout(resolve, pollInterval));
    }
    return null; // Timeout
  }, []);

  // Create person in both architectures with timing
  const createPerson = useCallback(async ({ name, email }) => {
    if (!name) return;

    let fedPersonId = null;

    // Create in Federation (with timing)
    addLog('federation', `Creating person: ${name}...`);
    let fedMutationTime = null;
    try {
      const fedStart = performance.now();
      const mutation = `
        mutation {
          createPerson(name: "${name}", email: "${email}") {
            id
            name
          }
        }
      `;
      const fedResponse = await fetch(FEDERATION_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: mutation })
      });
      const fedData = await fedResponse.json();
      fedMutationTime = Math.round(performance.now() - fedStart);
      fedPersonId = fedData?.data?.createPerson?.id;

      setMutationMetrics(prev => ({
        ...prev,
        federation: { mutationTime: fedMutationTime, totalTime: fedMutationTime, personName: name, personId: fedPersonId }
      }));

      // Update service status to show services are up
      setFederationMetrics(prev => ({
        ...prev,
        servicesUp: { hr: true, employment: true, security: true }
      }));

      addLog('federation', `Person created: ${name} in ${fedMutationTime}ms (data immediately available)`);

      // Auto-query the created person to show results
      if (fedPersonId) {
        queryFederation(fedPersonId);
      }
    } catch (e) {
      addLog('federation', `ERROR: ${e.message}`);
    }

    // Create in Event-Driven (with timing + propagation measurement)
    addLog('kafka', `Creating person: ${name}...`);
    try {
      const kafkaStart = performance.now();
      const response = await fetch(`${HR_EVENTS_URL}/api/persons`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, hireDate: new Date().toISOString().split('T')[0] })
      });
      const newPerson = await response.json();
      const mutationTime = Math.round(performance.now() - kafkaStart);

      addLog('kafka', `Mutation complete: ${mutationTime}ms. Waiting for Kafka propagation...`);

      // Now measure propagation time (how long until it appears in projection)
      const propagationTime = await waitForPropagation(newPerson.id);
      const totalTime = mutationTime + (propagationTime || 0);

      setMutationMetrics(prev => ({
        ...prev,
        eventDriven: { mutationTime, propagationTime, totalTime, personName: name, personId: newPerson?.id }
      }));

      // Update service status to show services are up
      setKafkaMetrics(prev => ({
        ...prev,
        servicesUp: { query: true, consumer: true, kafka: true }
      }));

      if (propagationTime !== null) {
        addLog('kafka', `Propagated in ${propagationTime}ms. Total: ${totalTime}ms (mutation: ${mutationTime}ms + propagation: ${propagationTime}ms)`);
      } else {
        addLog('kafka', `WARNING: Propagation timeout after 10s`);
      }

      // Refresh persons list and select the new person
      await fetchPersons();
      if (newPerson?.id) {
        setSelectedPerson(newPerson.id);
        // Auto-query the created person to show results
        queryKafka(newPerson.id);
      }
    } catch (e) {
      addLog('kafka', `ERROR: ${e.message}`);
    }
  }, [addLog, fetchPersons, waitForPropagation, queryKafka, queryFederation]);

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Header */}
      <header className="bg-gradient-to-r from-blue-800 to-purple-800 text-white p-4 shadow-lg">
        <div className="max-w-7xl mx-auto">
          <h1 className="text-2xl font-bold">Architecture Comparison Dashboard</h1>
          <p className="text-blue-200 text-sm">GraphQL Federation vs Kafka Projections</p>
        </div>
      </header>

      {/* Controls */}
      <div className="max-w-7xl mx-auto p-4">
        <div className="bg-white rounded-lg shadow p-4 mb-4 flex flex-wrap gap-4 items-center">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Select Person:</label>
            <select
              value={selectedPerson}
              onChange={(e) => setSelectedPerson(e.target.value)}
              className="border rounded px-3 py-2"
              disabled={loadingPersons}
            >
              {loadingPersons ? (
                <option>Loading...</option>
              ) : availablePersons.length === 0 ? (
                <option value="person-001">person-001 (default)</option>
              ) : (
                availablePersons.map(person => (
                  <option key={person.id} value={person.id}>
                    {person.name} ({person.id})
                  </option>
                ))
              )}
            </select>
          </div>
          <button onClick={queryBoth} className="btn-primary">
            Query Both Architectures
          </button>
          <button onClick={() => setShowCreateModal(true)} className="btn-secondary">
            Create Person
          </button>
        </div>

        {/* Comparison Summary */}
        <ComparisonSummary
          federationLatency={federationMetrics.latency}
          kafkaLatency={kafkaMetrics.latency}
          kafkaFreshness={kafkaMetrics.dataFreshness}
        />

        {/* Mutation Timing Comparison */}
        {(mutationMetrics.federation.totalTime || mutationMetrics.eventDriven.totalTime) && (
          <div className="bg-white rounded-lg shadow p-4 mt-4">
            <h2 className="text-lg font-bold text-gray-800 mb-2">Write Operation Comparison</h2>
            <p className="text-sm text-gray-600 mb-4">
              Created: <span className="font-semibold text-purple-600">{mutationMetrics.federation.personName || mutationMetrics.eventDriven.personName}</span>
            </p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Federation Write */}
              <div className="bg-blue-50 rounded-lg p-4">
                <h3 className="text-sm font-semibold text-blue-800 mb-2">GraphQL Federation</h3>
                <div className="text-3xl font-bold text-blue-600">
                  {mutationMetrics.federation.totalTime ? `${mutationMetrics.federation.totalTime}ms` : '-'}
                </div>
                <div className="text-xs text-gray-600 mt-1">
                  Synchronous write - data immediately available
                </div>
              </div>

              {/* Event-Driven Write */}
              <div className="bg-green-50 rounded-lg p-4">
                <h3 className="text-sm font-semibold text-green-800 mb-2">Event-Driven CQRS</h3>
                <div className="text-3xl font-bold text-green-600">
                  {mutationMetrics.eventDriven.totalTime ? `${mutationMetrics.eventDriven.totalTime}ms` : '-'}
                </div>
                {mutationMetrics.eventDriven.mutationTime && (
                  <div className="text-xs text-gray-600 mt-1">
                    <span className="font-medium">Mutation:</span> {mutationMetrics.eventDriven.mutationTime}ms +{' '}
                    <span className="font-medium">Propagation:</span> {mutationMetrics.eventDriven.propagationTime}ms
                  </div>
                )}
                <div className="text-xs text-yellow-700 mt-1">
                  Async write - eventual consistency via Kafka
                </div>
              </div>
            </div>

            {/* Insight */}
            {mutationMetrics.federation.totalTime && mutationMetrics.eventDriven.totalTime && (
              <div className="mt-4 p-3 bg-gray-100 rounded-lg text-sm text-gray-700">
                <strong>Write Tradeoff:</strong> Federation writes are{' '}
                <span className="font-bold text-blue-600">
                  {mutationMetrics.eventDriven.totalTime > mutationMetrics.federation.totalTime
                    ? `${Math.round((mutationMetrics.eventDriven.totalTime / mutationMetrics.federation.totalTime - 1) * 100)}% faster`
                    : 'similar speed'}
                </span>{' '}
                for immediate consistency. Event-Driven adds{' '}
                <span className="font-bold text-yellow-600">{mutationMetrics.eventDriven.propagationTime}ms</span>{' '}
                propagation delay but decouples services and enables event replay.
              </div>
            )}
          </div>
        )}

        {/* Side by Side Panels */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-4">
          <ArchitecturePanel
            title="GraphQL Federation"
            type="federation"
            metrics={federationMetrics}
            logs={logs.federation}
            onQuery={queryFederation}
            mutationTiming={mutationMetrics.federation}
          />
          <ArchitecturePanel
            title="Kafka Projections"
            type="kafka"
            metrics={kafkaMetrics}
            logs={logs.kafka}
            onQuery={queryKafka}
            mutationTiming={mutationMetrics.eventDriven}
          />
        </div>
      </div>

      {/* Create Person Modal */}
      <CreatePersonModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSubmit={createPerson}
      />
    </div>
  );
}

export default App;
