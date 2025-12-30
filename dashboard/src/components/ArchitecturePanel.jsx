import React from 'react';
import ArchitectureDiagram from './ArchitectureDiagram';

// Tooltip wrapper component
function Tooltip({ children, text }) {
  return (
    <span className="relative group cursor-help inline-block">
      {children}
      <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-2 bg-gray-900 text-white text-xs rounded-lg opacity-0 group-hover:opacity-100 transition-opacity z-50 pointer-events-none w-52 text-center leading-relaxed shadow-lg">
        {text}
        <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-gray-900"></span>
      </span>
    </span>
  );
}

function ArchitecturePanel({ title, type, metrics, logs, onQuery, mutationTiming }) {
  const isFederation = type === 'federation';

  return (
    <div className="architecture-panel">
      {/* Header */}
      <div className={`rounded-t-lg -m-4 mb-4 p-4 ${isFederation ? 'bg-blue-600' : 'bg-green-600'} text-white`}>
        <Tooltip text={isFederation
          ? "GraphQL Federation composes data from multiple subgraphs in real-time via a Router"
          : "Event-Driven CQRS uses Kafka events to build local projections for fast queries"
        }>
          <h2 className="text-xl font-bold border-b border-dotted border-white/50 inline-block">{title}</h2>
        </Tooltip>
        <p className="text-sm opacity-80 mt-1">
          {isFederation
            ? 'Synchronous composition, real-time data'
            : 'Asynchronous events, local projections'}
        </p>
      </div>

      {/* Architecture Diagram */}
      <div className="mb-4">
        <ArchitectureDiagram type={type} stageTiming={metrics.stageTiming} mutationTiming={mutationTiming} showExplanation={true} />
        {/* Timing explanation */}
        {isFederation && metrics.stageTiming?.hr && (
          <div className="mt-2 px-2 py-1 bg-gray-100 rounded text-xs text-gray-600">
            <strong>Reading the diagram:</strong> Subgraphs run in <em>parallel</em>, so Total = Overhead + max(subgraph times).
            DB times are <em>included</em> in subgraph times, not additive.
            {' '}<a href="https://www.apollographql.com/docs/federation/query-plans" target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">Learn more →</a>
          </div>
        )}
        {!isFederation && metrics.stageTiming?.queryService && (
          <div className="mt-2 px-2 py-1 bg-gray-100 rounded text-xs text-gray-600">
            <strong>Reading the diagram:</strong> Single service call to local projection database. No network hops to source services.
          </div>
        )}
      </div>

      {/* Service Status */}
      <div className="mb-4">
        <Tooltip text="Green = service responded to last health check. Red = service unavailable or not yet checked.">
          <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400 inline-block">Service Status</h3>
        </Tooltip>
        <div className="flex gap-4 text-sm">
          {isFederation ? (
            <>
              <ServiceStatus name="HR" up={metrics.servicesUp?.hr} tooltip="HR Subgraph - manages Person data (name, email, hire date)" />
              <ServiceStatus name="Employment" up={metrics.servicesUp?.employment} tooltip="Employment Subgraph - manages Employee data (title, department, salary)" />
              <ServiceStatus name="Security" up={metrics.servicesUp?.security} tooltip="Security Subgraph - manages Badge data (badge number, access level)" />
            </>
          ) : (
            <>
              <ServiceStatus name="Projection" up={metrics.servicesUp?.query} tooltip="Projection Service - serves pre-built views from local database" />
              <ServiceStatus name="Consumer" up={metrics.servicesUp?.consumer} tooltip="Kafka Consumer - processes events and updates local projections" />
              <ServiceStatus name="Kafka" up={metrics.servicesUp?.kafka} tooltip="Apache Kafka - message broker for event streaming" />
            </>
          )}
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-3 gap-2 mb-4">
        <MetricCard
          label="Latency"
          value={metrics.latency ? `${metrics.latency}ms` : '-'}
          highlight={isFederation ? 'text-orange-600' : 'text-green-600'}
          tooltip={isFederation
            ? "Total time for the query. Includes Router overhead + parallel subgraph calls. Higher due to network hops."
            : "Total time for the query. Single local database read. Much faster than Federation."
          }
        />
        <MetricCard
          label="Queries"
          value={metrics.queryCount}
          tooltip="Number of queries executed in this session. Use to compare throughput capabilities."
        />
        {isFederation ? (
          <MetricCard
            label="Services Called"
            value="3"
            sublabel="per query"
            tooltip="Each Federation query contacts all 3 subgraphs (HR, Employment, Security) to compose the response."
          />
        ) : (
          <MetricCard
            label="Data Freshness"
            value={metrics.dataFreshness}
            highlight="text-yellow-600"
            tooltip="Time since last Kafka event updated the projection. High = no recent source changes. In active systems, typically seconds."
          />
        )}
      </div>

      {/* Query Button */}
      <button
        onClick={onQuery}
        title={isFederation
          ? "Execute a GraphQL query through the Router to all subgraphs"
          : "Query the local projection database (no network calls to source services)"
        }
        className={`w-full py-2 rounded text-white font-medium ${
          isFederation ? 'bg-blue-600 hover:bg-blue-700' : 'bg-green-600 hover:bg-green-700'
        }`}
      >
        {isFederation ? 'Query Composed View' : 'Query Local Projection'}
      </button>

      {/* Last Query Result */}
      {metrics.lastQuery && (
        <div className="mt-4">
          <Tooltip text="The raw JSON response from the last query. Shows the composed Person/Employee/Badge data.">
            <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400 inline-block">Last Query Result</h3>
          </Tooltip>
          <pre className="bg-gray-100 p-2 rounded text-xs overflow-auto max-h-32">
            {JSON.stringify(metrics.lastQuery, null, 2)}
          </pre>
        </div>
      )}

      {/* Logs */}
      <div className="mt-4">
        <Tooltip text="Real-time activity log showing query timing breakdowns and any errors.">
          <h3 className="text-sm font-semibold text-gray-600 mb-2 border-b border-dotted border-gray-400 inline-block">Activity Log</h3>
        </Tooltip>
        <div className="bg-gray-900 text-green-400 p-2 rounded h-32 overflow-auto font-mono text-xs">
          {logs.map((log, i) => (
            <div key={i} className="py-0.5">
              <span className="text-gray-500">{log.timestamp}</span> {log.message}
            </div>
          ))}
          {logs.length === 0 && <div className="text-gray-500">No activity yet...</div>}
        </div>
      </div>
    </div>
  );
}

function ServiceStatus({ name, up, tooltip }) {
  return (
    <Tooltip text={tooltip}>
      <div className="flex items-center">
        <span className={`status-indicator ${up ? 'status-up' : 'status-down'}`} />
        {name}
      </div>
    </Tooltip>
  );
}

function MetricCard({ label, value, sublabel, highlight, tooltip }) {
  return (
    <Tooltip text={tooltip}>
      <div className="metric-card cursor-help">
        <div className={`text-xl font-bold ${highlight || ''}`}>{value}</div>
        <div className="text-xs text-gray-500 border-b border-dotted border-gray-300">{label}</div>
        {sublabel && <div className="text-xs text-gray-400">{sublabel}</div>}
      </div>
    </Tooltip>
  );
}

export default ArchitecturePanel;
