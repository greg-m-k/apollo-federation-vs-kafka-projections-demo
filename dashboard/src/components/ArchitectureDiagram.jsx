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

function ArchitectureDiagram({ type, stageTiming, mutationTiming }) {
  const containerRef = useRef(null);

  // Check if we have mutation timing to show write flow
  const showWriteFlow = mutationTiming?.mutationTime != null;

  // Build Federation diagram with timing
  const federationDiagram = useMemo(() => {
    if (showWriteFlow) {
      // Write flow diagram for Federation with comprehensive timing
      const totalTime = mutationTiming?.mutationTime || 0;
      const networkTime = Math.round(totalTime * 0.10);
      const routerTime = Math.round(totalTime * 0.08);
      const subgraphTime = Math.round(totalTime * 0.35);
      const dbTime = Math.round(totalTime * 0.47);

      const netLabel = totalTime ? `|${networkTime}ms|` : '';
      const routerLabel = totalTime ? `|${routerTime}ms|` : '';
      const subgraphLabel = totalTime ? `|${subgraphTime}ms|` : '';
      const dbLabel = totalTime ? `|${dbTime}ms|` : '';

      return `
graph TD
    Client[Client] -->${netLabel} Router[Router]
    Router -->${subgraphLabel} HR[HR Subgraph]
    HR -->${dbLabel} HR_DB[(HR DB)]
    HR_DB -.->|committed| Response[Response]
    Response -.-> Client

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd,stroke:#2196f3,stroke-width:3px
    style HR fill:#fff3e0,stroke:#ff9800,stroke-width:3px
    style HR_DB fill:#ffecb3,stroke:#ff9800,stroke-width:2px
    style Response fill:#c8e6c9,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram with comprehensive timing on ALL edges
    const hasTimingData = stageTiming?.router != null;
    const networkTime = stageTiming?.network ? `|${stageTiming.network}ms|` : '';
    const routerTime = stageTiming?.router ? `|${stageTiming.router}ms|` : '';
    const hrTime = stageTiming?.hr ? `|${stageTiming.hr}ms|` : '';
    const hrDbTime = stageTiming?.hrDb ? `|${stageTiming.hrDb}ms|` : '';
    const empTime = stageTiming?.employment ? `|${stageTiming.employment}ms|` : '';
    const empDbTime = stageTiming?.employmentDb ? `|${stageTiming.employmentDb}ms|` : '';
    const secTime = stageTiming?.security ? `|${stageTiming.security}ms|` : '';
    const secDbTime = stageTiming?.securityDb ? `|${stageTiming.securityDb}ms|` : '';

    return `
graph TD
    Client[Client] -->${networkTime} Router[Router]
    Router -->${hrTime} HR[HR Subgraph]
    Router -->${empTime} Emp[Employment]
    Router -->${secTime} Sec[Security]
    HR -->${hrDbTime} HR_DB[(HR DB)]
    Emp -->${empDbTime} Emp_DB[(Emp DB)]
    Sec -->${secDbTime} Sec_DB[(Sec DB)]

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style HR fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Emp fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Sec fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style HR_DB fill:#ffecb3${hasTimingData ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Emp_DB fill:#ffecb3${hasTimingData ? ',stroke:#d32f2f,stroke-width:2px' : ''}
    style Sec_DB fill:#ffecb3${hasTimingData ? ',stroke:#d32f2f,stroke-width:2px' : ''}
`;
  }, [stageTiming, mutationTiming, showWriteFlow]);

  // Build Kafka Projections diagram with timing
  const kafkaDiagram = useMemo(() => {
    if (showWriteFlow) {
      // Write flow diagram for Event-Driven with comprehensive timing
      const mutTime = mutationTiming?.mutationTime || 0;
      const propTime = mutationTiming?.propagationTime || 0;

      // Break down mutation time: network (10%), service processing (20%), DB write (50%), outbox (20%)
      const netTime = Math.round(mutTime * 0.10);
      const svcTime = Math.round(mutTime * 0.20);
      const dbTime = Math.round(mutTime * 0.50);
      const outboxTime = Math.round(mutTime * 0.20);

      // Break down propagation: Kafka delivery (30%), consumer processing (30%), projection write (40%)
      const kafkaTime = Math.round(propTime * 0.30);
      const consumerTime = Math.round(propTime * 0.30);
      const projTime = Math.round(propTime * 0.40);

      const netLabel = mutTime ? `|${netTime}ms|` : '';
      const svcLabel = mutTime ? `|${svcTime}ms|` : '';
      const dbLabel = mutTime ? `|${dbTime}ms|` : '';
      const outboxLabel = mutTime ? `|${outboxTime}ms|` : '';
      const kafkaLabel = propTime ? `|${kafkaTime}ms|` : '';
      const consumerLabel = propTime ? `|${consumerTime}ms|` : '';
      const projLabel = propTime ? `|${projTime}ms|` : '';

      return `
graph TD
    Client[Client] -->${netLabel} Events[HR Events Svc]
    Events -->${dbLabel} DB[(HR DB)]
    DB -->${outboxLabel} Outbox[Outbox]
    Outbox -->${kafkaLabel} Kafka[Kafka]
    Kafka -->${consumerLabel} Consumer[Consumer]
    Consumer -->${projLabel} Projection[(Projection)]
    Projection -.->|available| Ready[Query Ready]

    style Client fill:#e1f5fe
    style Events fill:#c8e6c9,stroke:#4caf50,stroke-width:3px
    style DB fill:#e8f5e9,stroke:#4caf50,stroke-width:2px
    style Outbox fill:#fff3e0,stroke:#ff9800,stroke-width:2px
    style Kafka fill:#ffcdd2,stroke:#f44336,stroke-width:2px
    style Consumer fill:#e8f5e9,stroke:#4caf50,stroke-width:2px
    style Projection fill:#c8e6c9,stroke:#4caf50,stroke-width:3px
    style Ready fill:#a5d6a7,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram with comprehensive timing
    const hasTimingData = stageTiming?.queryService != null;
    const qsTime = stageTiming?.queryService ? `|${stageTiming.queryService}ms|` : '';
    // Estimate DB query is ~80% of query service time
    const dbTime = stageTiming?.queryService ? `|${Math.round(stageTiming.queryService * 0.8)}ms|` : '';

    return `
graph TD
    Client[Client] -->${qsTime} QS[Projection Svc]
    QS -->${dbTime} Local[(Local Projections)]

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
    </div>
  );
}

export default ArchitectureDiagram;
