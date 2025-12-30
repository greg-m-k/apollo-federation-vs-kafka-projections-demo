import React, { useEffect, useRef, useMemo } from 'react';
import mermaid from 'mermaid';

mermaid.initialize({
  startOnLoad: false,
  theme: 'neutral',
  securityLevel: 'loose',
  themeCSS: `
    .timing-label { fill: #d32f2f !important; font-style: italic !important; font-weight: bold !important; }
  `
});

function ArchitectureDiagram({ type, stageTiming, mutationTiming, showExplanation }) {
  const containerRef = useRef(null);

  // Check if we have mutation timing to show write flow
  const showWriteFlow = mutationTiming?.mutationTime != null;

  // Build Federation diagram with timing
  // Only shows REAL measured timing - no fake percentages
  const federationDiagram = useMemo(() => {
    if (showWriteFlow) {
      // Write flow diagram for Federation - shows timing on each edge
      const routerOverhead = mutationTiming?.routerOverhead;
      const hrTime = mutationTiming?.hrTime;
      const hrDbTime = mutationTiming?.hrDbTime;
      const totalTime = mutationTiming?.mutationTime;

      const overheadLabel = routerOverhead != null ? `|${routerOverhead}ms|` : '';
      const hrLabel = hrTime != null ? `|${hrTime}ms|` : '';
      const dbLabel = hrDbTime != null ? `|${hrDbTime}ms|` : '';

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

    // Calculate router overhead (e2e - max subgraph time)
    const e2eTime = stageTiming?.total;
    const maxSubgraphTime = Math.max(stageTiming?.hr || 0, stageTiming?.employment || 0, stageTiming?.security || 0);
    const routerTime = (e2eTime && maxSubgraphTime) ? Math.max(0, e2eTime - maxSubgraphTime) : null;

    // Per-subgraph timing labels
    const clientRouterTime = routerTime != null ? `|${routerTime}ms|` : '';
    const hrTime = hasHrTiming ? `|${stageTiming.hr}ms|` : '';
    const empTime = hasEmpTiming ? `|${stageTiming.employment}ms|` : '';
    const secTime = hasSecTiming ? `|${stageTiming.security}ms|` : '';

    // DB query timing labels
    const hrDbTime = stageTiming?.hrDb != null ? `|${stageTiming.hrDb}ms|` : '';
    const empDbTime = stageTiming?.employmentDb != null ? `|${stageTiming.employmentDb}ms|` : '';
    const secDbTime = stageTiming?.securityDb != null ? `|${stageTiming.securityDb}ms|` : '';

    return `
graph TD
    Client[Client] ${clientRouterTime ? `-->${clientRouterTime}` : '-->'} Router[Router]
    Router ${hrTime ? `-->${hrTime}` : '-->'} HR[HR Subgraph]
    Router ${empTime ? `-->${empTime}` : '-->'} Emp[Employment]
    Router ${secTime ? `-->${secTime}` : '-->'} Sec[Security]
    HR ${hrDbTime ? `-->${hrDbTime}` : '-->'} HR_DB[(HR DB)]
    Emp ${empDbTime ? `-->${empDbTime}` : '-->'} Emp_DB[(Emp DB)]
    Sec ${secDbTime ? `-->${secDbTime}` : '-->'} Sec_DB[(Sec DB)]

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd${hasAnyTiming ? ',stroke:#2196f3,stroke-width:3px' : ''}
    style HR fill:#fff3e0${hasHrTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Emp fill:#fff3e0${hasEmpTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Sec fill:#fff3e0${hasSecTiming ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style HR_DB fill:#ffecb3${stageTiming?.hrDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Emp_DB fill:#ffecb3${stageTiming?.employmentDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Sec_DB fill:#ffecb3${stageTiming?.securityDb != null ? ',stroke:#d32f2f,stroke-width:2px' : ''}
`;
  }, [stageTiming, mutationTiming, showWriteFlow]);

  // Build Kafka Projections diagram with timing
  // Only shows REAL measured timing - no fake percentages
  const kafkaDiagram = useMemo(() => {
    if (showWriteFlow) {
      // Write flow diagram for Event-Driven - shows real measured times on every edge
      const mutTime = mutationTiming?.mutationTime || 0;
      const dbWriteTime = mutationTiming?.dbWriteTime;
      const outboxWriteTime = mutationTiming?.outboxWriteTime;
      const outboxToKafkaMs = mutationTiming?.outboxToKafkaMs;
      const consumerToProjectionMs = mutationTiming?.consumerToProjectionMs;

      // Show timing on each edge where we have data
      const clientToSvcLabel = mutTime ? `|${mutTime}ms|` : '';
      const dbLabel = dbWriteTime != null ? `|${dbWriteTime}ms|` : '';
      const outboxLabel = outboxWriteTime != null ? `|${outboxWriteTime}ms|` : '';
      const outboxToKafkaLabel = outboxToKafkaMs != null ? `|${outboxToKafkaMs}ms|` : '';
      const consumerToProjectionLabel = consumerToProjectionMs != null ? `|${consumerToProjectionMs}ms|` : '';

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
    const hasTimingData = stageTiming?.queryService != null;
    const qsTime = stageTiming?.queryService ? `|${stageTiming.queryService}ms|` : '';
    // For Kafka, the db lookup is essentially the queryService time (single-hop)
    const dbTime = qsTime;

    return `
graph TD
    Client[Client] ${qsTime ? `-->${qsTime}` : '-->'} QS[Projection Svc]
    QS ${dbTime ? `-->${dbTime}` : '-->'} Local[(Local Projections)]

    Sources[Source Services] --> Kafka[Kafka]
    Kafka --> Consumer[Consumer]
    Consumer -.->|async| Local

    style Client fill:#e1f5fe
    style QS fill:#c8e6c9${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Local fill:#c8e6c9${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Kafka fill:#ffcdd2
    style Sources fill:#fff3e0
    style Consumer fill:#e8f5e9
`;
  }, [stageTiming, mutationTiming, showWriteFlow]);

  useEffect(() => {
    const renderDiagram = async () => {
      if (containerRef.current) {
        const diagram = type === 'federation' ? federationDiagram : kafkaDiagram;
        const id = `mermaid-${type}-${Date.now()}`;

        try {
          const { svg } = await mermaid.render(id, diagram);
          containerRef.current.innerHTML = svg;

          // Style edge labels (timing) to be red, bold, italic
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
              });
              // Also style the label container itself
              label.style.setProperty('color', '#d32f2f', 'important');
            }
          });
        } catch (e) {
          console.error('Mermaid render error:', e);
        }
      }
    };

    renderDiagram();
  }, [type, federationDiagram, kafkaDiagram]);

  return (
    <div className="relative">
      {showWriteFlow && (
        <div className="absolute top-1 right-2 text-xs font-medium text-orange-600 bg-orange-100 px-2 py-0.5 rounded">
          Write: {mutationTiming?.personName || 'New Person'}
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
