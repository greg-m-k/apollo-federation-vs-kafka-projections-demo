import React, { useEffect, useRef } from 'react';
import mermaid from 'mermaid';

mermaid.initialize({
  startOnLoad: false,
  theme: 'neutral',
  securityLevel: 'loose',
  themeCSS: `
    .timing-label { fill: #d32f2f !important; font-style: italic !important; font-weight: bold !important; }
  `
});

interface StageTiming {
  hr?: number | null;
  employment?: number | null;
  security?: number | null;
  hrDb?: number | null;
  employmentDb?: number | null;
  securityDb?: number | null;
  total?: number | null;
  queryService?: number | null;
  db?: number | null;
}

interface MutationTiming {
  mutationTime?: number | null;
  totalTime?: number | null;
  personName?: string | null;
  routerOverhead?: number | null;
  hrTime?: number | null;
  hrDbTime?: number | null;
  propagationTime?: number | null;
  dbWriteTime?: number | null;
  outboxWriteTime?: number | null;
  outboxToKafkaMs?: number | null;
  consumerToProjectionMs?: number | null;
}

interface LastQueryData {
  data?: {
    person?: {
      name?: string;
    };
  };
  name?: string;
}

interface ArchitectureDiagramProps {
  type: 'federation' | 'kafka';
  stageTiming?: StageTiming;
  mutationTiming?: MutationTiming;
  lastQuery?: LastQueryData | null;
  showExplanation?: boolean;
  isCreating?: boolean;
  showWriteFlow: boolean;
}

function ArchitectureDiagram({ type, stageTiming, mutationTiming, lastQuery, showExplanation, isCreating, showWriteFlow }: ArchitectureDiagramProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Get the queried person name for read label
  const queriedPersonName = lastQuery?.data?.person?.name || lastQuery?.name || null;

  // Build Federation diagram with timing
  // Only shows REAL measured timing - no fake percentages
  const federationDiagram = (() => {
    if (showWriteFlow) {
      // Write flow diagram for Federation - shows timing on each edge
      const routerOverhead = mutationTiming?.routerOverhead;
      const hrTime = mutationTiming?.hrTime;
      const hrDbTime = mutationTiming?.hrDbTime;
      const totalTime = mutationTiming?.mutationTime;

      const overheadLabel = routerOverhead != null ? `|${routerOverhead}ms |` : '';
      const hrLabel = hrTime != null ? `|${hrTime}ms |` : '';
      const dbLabel = hrDbTime != null ? `|${hrDbTime}ms |` : '';

      return `
graph TD
    Client[Client] ${overheadLabel ? `-->${overheadLabel}Router[Router]` : '--> Router[Router]'}
    Router ${hrLabel ? `-->${hrLabel}HR[HR Subgraph]` : '--> HR[HR Subgraph]'}
    HR ${dbLabel ? `-->${dbLabel}HR_DB[(HR DB)]` : '--> HR_DB[(HR DB)]'}
    HR_DB -.->|committed| Response[Response]
    Response -.-> Client

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd${routerOverhead != null ? ',stroke:#d32f2f,stroke-width:3px' : ',stroke:#2196f3,stroke-width:3px'}
    style HR fill:#fff3e0${hrTime != null ? ',stroke:#d32f2f,stroke-width:3px' : ',stroke:#ff9800,stroke-width:3px'}
    style HR_DB fill:#ffecb3${hrDbTime != null ? ',stroke:#d32f2f,stroke-width:2px' : ',stroke:#ff9800,stroke-width:2px'}
    style Response fill:#c8e6c9,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram - shows per-subgraph timing from response headers
    const hasHrTiming = stageTiming?.hr != null;
    const hasEmpTiming = stageTiming?.employment != null;
    const hasSecTiming = stageTiming?.security != null;
    const hasAnyTiming = hasHrTiming || hasEmpTiming || hasSecTiming;

    // Calculate router overhead (round-trip - max subgraph time)
    const roundTripTime = stageTiming?.total;
    const maxSubgraphTime = Math.max(stageTiming?.hr || 0, stageTiming?.employment || 0, stageTiming?.security || 0);
    const routerTime = (roundTripTime && maxSubgraphTime) ? Math.max(0, roundTripTime - maxSubgraphTime) : null;

    // Per-subgraph timing labels - using proper Mermaid pipe syntax for edge labels
    const clientRouterLabel = routerTime != null ? `|${routerTime}ms|` : '';
    const hrLabel = hasHrTiming ? `|${stageTiming.hr}ms|` : '';
    const empLabel = hasEmpTiming ? `|${stageTiming.employment}ms|` : '';
    const secLabel = hasSecTiming ? `|${stageTiming.security}ms|` : '';

    // DB query timing labels - using proper Mermaid pipe syntax
    const hrDbLabel = stageTiming?.hrDb != null ? `|${stageTiming.hrDb}ms|` : '';
    const empDbLabel = stageTiming?.employmentDb != null ? `|${stageTiming.employmentDb}ms|` : '';
    const secDbLabel = stageTiming?.securityDb != null ? `|${stageTiming.securityDb}ms|` : '';

    return `
graph TD
    Client[Client] ${clientRouterLabel ? `<-->${clientRouterLabel}` : '-.-'} Router[Router]
    Router ${hrLabel ? `<-->${hrLabel}` : '-.-'} HR[HR Subgraph]
    Router ${empLabel ? `<-->${empLabel}` : '-.-'} Emp[Employment]
    Router ${secLabel ? `<-->${secLabel}` : '-.-'} Sec[Security]
    HR ${hrDbLabel ? `---${hrDbLabel}` : '-.-'} HR_DB[(HR DB)]
    Emp ${empDbLabel ? `---${empDbLabel}` : '-.-'} Emp_DB[(Emp DB)]
    Sec ${secDbLabel ? `---${secDbLabel}` : '-.-'} Sec_DB[(Sec DB)]

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd${hasAnyTiming ? ',stroke:#2196f3,stroke-width:3px' : ''}
    style HR fill:#fff3e0${hasHrTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Emp fill:#fff3e0${hasEmpTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Sec fill:#fff3e0${hasSecTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style HR_DB fill:#ffecb3${stageTiming?.hrDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Emp_DB fill:#ffecb3${stageTiming?.employmentDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Sec_DB fill:#ffecb3${stageTiming?.securityDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
`;
  })();

  // Build Event-Driven Projections diagram with timing
  // Only shows REAL measured timing - no fake percentages
  const kafkaDiagram = (() => {
    if (showWriteFlow) {
      // Write flow diagram for Event-Driven - shows real measured times on every edge
      const mutTime = mutationTiming?.mutationTime || 0;
      const dbWriteTime = mutationTiming?.dbWriteTime;
      const outboxWriteTime = mutationTiming?.outboxWriteTime;
      const outboxToKafkaMs = mutationTiming?.outboxToKafkaMs;
      const consumerToProjectionMs = mutationTiming?.consumerToProjectionMs;

      // Show timing on each edge where we have data
      const clientToSvcLabel = mutTime ? `|${mutTime}ms |` : '';
      const dbLabel = dbWriteTime != null ? `|${dbWriteTime}ms |` : '';
      const outboxLabel = outboxWriteTime != null ? `|${outboxWriteTime}ms |` : '';
      const outboxToKafkaLabel = outboxToKafkaMs != null ? `|${outboxToKafkaMs}ms |` : '';
      const consumerToProjectionLabel = consumerToProjectionMs != null ? `|${consumerToProjectionMs}ms |` : '';
      return `
graph TD
    Client[Client] ${clientToSvcLabel ? `-->${clientToSvcLabel}Events[HR Events Svc]` : '--> Events[HR Events Svc]'}
    Events ${dbLabel ? `-->${dbLabel}DB[(HR DB)]` : '--> DB[(HR DB)]'}
    Events ${outboxLabel ? `-->${outboxLabel}Outbox[(Outbox)]` : '--> Outbox[(Outbox)]'}
    Outbox ${outboxToKafkaLabel ? `-.->|${outboxToKafkaMs}ms|Kafka[Kafka]` : '-.-> Kafka[Kafka]'}
    Kafka -.->|poll| Consumer[Consumer]
    Consumer ${consumerToProjectionLabel ? `-.->|${consumerToProjectionMs}ms|Projection[(Projection)]` : '-.-> Projection[(Projection)]'}
    Projection -.-> Fresh[Data Fresh]

    style Client fill:#e1f5fe
    style Events fill:#c8e6c9${mutTime ? ',stroke:#d32f2f,stroke-width:3px' : ',stroke:#4caf50,stroke-width:3px'}
    style DB fill:#e8f5e9${dbWriteTime != null ? ',stroke:#d32f2f,stroke-width:2px' : ',stroke:#4caf50,stroke-width:2px'}
    style Outbox fill:#fff3e0${outboxWriteTime != null ? ',stroke:#d32f2f,stroke-width:2px' : ',stroke:#ff9800,stroke-width:2px'}
    style Kafka fill:#ffcdd2${outboxToKafkaMs != null ? ',stroke:#d32f2f,stroke-width:2px' : ',stroke:#f44336,stroke-width:2px'}
    style Consumer fill:#e8f5e9${consumerToProjectionMs != null ? ',stroke:#d32f2f,stroke-width:2px' : ',stroke:#4caf50,stroke-width:2px'}
    style Projection fill:#c8e6c9${consumerToProjectionMs != null ? ',stroke:#d32f2f,stroke-width:3px' : ',stroke:#4caf50,stroke-width:3px'}
    style Fresh fill:#a5d6a7,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram - shows real query service timing
    // Calculate network overhead (round-trip - backend processing time)
    const roundTripTime = stageTiming?.total;
    const queryServiceTime = stageTiming?.queryService;
    const networkOverhead = (roundTripTime && queryServiceTime) ? Math.max(0, roundTripTime - queryServiceTime) : null;

    const hasTimingData = queryServiceTime != null;
    const hasDbTiming = stageTiming?.db != null;
    const hasNetworkTiming = networkOverhead != null;

    // Network overhead on Client-QS edge - using proper Mermaid pipe syntax
    const networkLabel = hasNetworkTiming ? `|${networkOverhead}ms|` : '';
    // DB time on QS-Local edge
    const dbLabel = hasDbTiming ? `|${stageTiming.db}ms|` : '';

    return `
graph TD
    Client[Client] ${networkLabel ? `<-->${networkLabel}` : '-.-'} QS[Projection Svc]
    QS ${dbLabel ? `---${dbLabel}` : '-.-'} Local[(Local Projections)]

    Sources[Source Services] --> Kafka[Kafka]
    Kafka --> Consumer[Consumer]
    Consumer -.->|async| Local

    style Client fill:#e1f5fe
    style QS fill:#c8e6c9${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Local fill:#c8e6c9${hasDbTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Kafka fill:#ffcdd2
    style Sources fill:#fff3e0
    style Consumer fill:#e8f5e9
`;
  })();

  useEffect(() => {
    const renderDiagram = async () => {
      if (containerRef.current) {
        const diagram = type === 'federation' ? federationDiagram : kafkaDiagram;
        const id = `mermaid-${type}-${Date.now()}`;

        try {
          const { svg } = await mermaid.render(id, diagram);
          containerRef.current.innerHTML = svg;

          // Style edge labels (timing) to be red, bold, italic, smaller
          const edgeLabels = containerRef.current.querySelectorAll('.edgeLabel');
          edgeLabels.forEach(label => {
            const text = label.textContent;
            if (text && text.includes('ms')) {
              // Style all child elements
              label.querySelectorAll('*').forEach(el => {
                el.style.setProperty('color', '#d32f2f', 'important');
                el.style.setProperty('fill', '#d32f2f', 'important');
                el.style.setProperty('font-weight', 'bold', 'important');
                el.style.setProperty('font-style', 'italic', 'important');
                el.style.setProperty('font-size', '0.7em', 'important');
              });
              // Also style the label container itself
              label.style.setProperty('color', '#d32f2f', 'important');
              label.style.setProperty('font-size', '0.7em', 'important');
            }
          });
        } catch (e) {
          console.error('Mermaid render error:', e);
        }
      }
    };

    renderDiagram();
  }, [type, federationDiagram, kafkaDiagram, stageTiming, showWriteFlow, mutationTiming]);

  return (
    <div className="relative">
      {/* Loading overlay */}
      {isCreating && (
        <div className="absolute inset-0 bg-white/80 z-10 flex items-center justify-center rounded">
          <div className="flex flex-col items-center gap-2">
            <svg className={`animate-spin h-8 w-8 ${type === 'federation' ? 'text-blue-600' : 'text-green-600'}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span className="text-sm font-medium text-gray-600">
              {type === 'federation' ? 'Creating person...' : 'Creating & propagating...'}
            </span>
          </div>
        </div>
      )}
      {showWriteFlow && (
        <div className="absolute top-1 right-2 text-xs font-medium text-orange-600 bg-orange-100 px-2 py-0.5 rounded">
          Write: {mutationTiming?.personName || 'New Person'}
        </div>
      )}
      {!showWriteFlow && queriedPersonName && (
        <div className="absolute top-1 right-2 text-xs font-medium text-blue-600 bg-blue-100 px-2 py-0.5 rounded">
          Read: {queriedPersonName}
        </div>
      )}
      <div
        ref={containerRef}
        className="bg-gray-50 rounded p-2 flex justify-center min-h-[180px]"
      />
      {showExplanation && showWriteFlow && (
        <div className="mt-2 p-2 bg-amber-50 border border-amber-200 rounded text-sm text-amber-800">
          <span className="font-medium">Above:</span> Write flow with measured timing on each edge. <span className="font-medium">Below:</span> Query results immediately after creation.
        </div>
      )}
    </div>
  );
}

export default ArchitectureDiagram;
