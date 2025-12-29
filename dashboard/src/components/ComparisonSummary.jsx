import React from 'react';

// Tooltip wrapper component
function Tooltip({ children, text }) {
  return (
    <span className="relative group cursor-help">
      {children}
      <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-2 bg-gray-900 text-white text-xs rounded-lg opacity-0 group-hover:opacity-100 transition-opacity z-50 pointer-events-none w-56 text-center leading-relaxed shadow-lg">
        {text}
        <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-gray-900"></span>
      </span>
    </span>
  );
}

function ComparisonSummary({ federationLatency, kafkaLatency, kafkaFreshness }) {
  const latencyDiff = federationLatency && kafkaLatency
    ? Math.round((federationLatency - kafkaLatency) / kafkaLatency * 100)
    : null;

  return (
    <div className="bg-white rounded-lg shadow p-4">
      <h2 className="text-lg font-bold text-gray-800 mb-4">Tradeoff Comparison</h2>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Latency Comparison */}
        <div className="bg-gradient-to-br from-blue-50 to-green-50 rounded-lg p-4">
          <Tooltip text="Time from request to response. Federation makes multiple network calls; Event-Driven queries local data.">
            <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400">Query Latency</h3>
          </Tooltip>
          <div className="flex justify-between items-center">
            <div className="text-center">
              <Tooltip text="Federation latency is additive: Router + all subgraph calls in parallel. Higher latency but always fresh data.">
                <div className="text-2xl font-bold text-blue-600">
                  {federationLatency ? `${federationLatency}ms` : '-'}
                </div>
              </Tooltip>
              <div className="text-xs text-gray-500">Federation</div>
            </div>
            <div className="text-gray-400">vs</div>
            <div className="text-center">
              <Tooltip text="Kafka Projections latency is just one local database query. Much faster but data may be slightly stale.">
                <div className="text-2xl font-bold text-green-600">
                  {kafkaLatency ? `${kafkaLatency}ms` : '-'}
                </div>
              </Tooltip>
              <div className="text-xs text-gray-500">Kafka</div>
            </div>
          </div>
          {latencyDiff !== null && (
            <div className="text-center mt-2 text-sm text-gray-600">
              Event-Driven is <span className="font-bold text-green-600">{latencyDiff}%</span> faster
            </div>
          )}
        </div>

        {/* Consistency Model */}
        <div className="bg-gradient-to-br from-yellow-50 to-orange-50 rounded-lg p-4">
          <Tooltip text="How fresh is the data? Federation always gets current data; Event-Driven uses cached projections.">
            <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400">Data Consistency</h3>
          </Tooltip>
          <div className="flex justify-between items-center">
            <div className="text-center">
              <Tooltip text="Federation queries source databases directly. Data is always current, but requires all services to be available.">
                <div className="text-lg font-bold text-blue-600">Real-time</div>
              </Tooltip>
              <div className="text-xs text-gray-500">Federation</div>
            </div>
            <div className="text-gray-400">vs</div>
            <div className="text-center">
              <Tooltip text="Time since last Kafka event updated the projection. High values mean no recent changes to source data. In active systems this is typically seconds.">
                <div className="text-lg font-bold text-yellow-600">{kafkaFreshness}</div>
              </Tooltip>
              <div className="text-xs text-gray-500">Data Freshness</div>
            </div>
          </div>
          <div className="text-center mt-2 text-xs text-gray-500">
            Eventually consistent with lag indicator
          </div>
        </div>

        {/* Availability */}
        <div className="bg-gradient-to-br from-red-50 to-pink-50 rounded-lg p-4">
          <Tooltip text="What happens when a source service goes down? This shows the availability tradeoff.">
            <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400">Failure Mode</h3>
          </Tooltip>
          <div className="space-y-2 text-sm">
            <Tooltip text="Federation requires all subgraphs to respond. If HR, Employment, or Security is down, the entire query fails.">
              <div className="flex items-center">
                <span className="w-24 text-blue-600 font-medium">Federation:</span>
                <span className="text-gray-600">1 service down = query fails</span>
              </div>
            </Tooltip>
            <Tooltip text="Kafka Projections queries local database only. Even if source services are down, queries succeed with the last known data.">
              <div className="flex items-center">
                <span className="w-24 text-green-600 font-medium">Kafka:</span>
                <span className="text-gray-600">Query works (stale data)</span>
              </div>
            </Tooltip>
          </div>
        </div>
      </div>

      {/* Key Insight */}
      <div className="mt-4 p-3 bg-gray-100 rounded-lg text-sm text-gray-700">
        <strong>Key Insight:</strong> Federation provides real-time data consistency but couples availability
        to all services. Kafka Projections decouples services at the cost of eventual consistency.
      </div>
    </div>
  );
}

export default ComparisonSummary;
