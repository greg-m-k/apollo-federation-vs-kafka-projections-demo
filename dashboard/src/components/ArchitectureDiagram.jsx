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
      // Write flow diagram for Federation
      const mutTime = mutationTiming?.mutationTime ? `|${mutationTiming.mutationTime}ms|` : '';
      return `
graph TD
    Client[Client] -->${mutTime} Router[Router]
    Router --> HR[HR Subgraph]
    HR --> HR_DB[(HR DB)]
    HR_DB -.->|committed| Response[Response]
    Response -.-> Client

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd,stroke:#2196f3,stroke-width:3px
    style HR fill:#fff3e0,stroke:#ff9800,stroke-width:3px
    style HR_DB fill:#ffecb3
    style Response fill:#c8e6c9,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram (existing)
    const hasTimingData = stageTiming?.router != null;
    const routerTime = stageTiming?.router ? `|${stageTiming.router}ms|` : '';
    const hrTime = stageTiming?.hr ? `|${stageTiming.hr}ms|` : '';
    const empTime = stageTiming?.employment ? `|${stageTiming.employment}ms|` : '';
    const secTime = stageTiming?.security ? `|${stageTiming.security}ms|` : '';

    return `
graph TD
    Client[Client] -->${routerTime} Router[Router]
    Router -->${hrTime} HR[HR Subgraph]
    Router -->${empTime} Emp[Employment]
    Router -->${secTime} Sec[Security]
    HR --> HR_DB[(HR DB)]
    Emp --> Emp_DB[(Emp DB)]
    Sec --> Sec_DB[(Sec DB)]

    style Client fill:#e1f5fe
    style Router fill:#e3f2fd${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style HR fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Emp fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
    style Sec fill:#fff3e0${hasTimingData ? ',stroke:#d32f2f,stroke-width:3px' : ''}
`;
  }, [stageTiming, mutationTiming, showWriteFlow]);

  // Build Kafka Projections diagram with timing
  const kafkaDiagram = useMemo(() => {
    if (showWriteFlow) {
      // Write flow diagram for Event-Driven - shows the full Kafka pipeline
      const mutTime = mutationTiming?.mutationTime ? `|${mutationTiming.mutationTime}ms|` : '';
      const propTime = mutationTiming?.propagationTime ? `|${mutationTiming.propagationTime}ms|` : '';

      return `
graph TD
    Client[Client] -->${mutTime} Events[HR Events Svc]
    Events --> DB[(HR DB)]
    DB --> Outbox[Outbox]
    Outbox --> Kafka[Kafka]
    Kafka -->${propTime} Consumer[Consumer]
    Consumer --> Projection[(Projection)]
    Projection -.->|available| Ready[Query Ready]

    style Client fill:#e1f5fe
    style Events fill:#c8e6c9,stroke:#4caf50,stroke-width:3px
    style DB fill:#e8f5e9
    style Outbox fill:#fff3e0
    style Kafka fill:#ffcdd2,stroke:#f44336,stroke-width:2px
    style Consumer fill:#e8f5e9,stroke:#4caf50,stroke-width:2px
    style Projection fill:#c8e6c9,stroke:#4caf50,stroke-width:3px
    style Ready fill:#a5d6a7,stroke:#4caf50,stroke-width:2px
`;
    }

    // Read flow diagram (existing)
    const hasTimingData = stageTiming?.queryService != null;
    const qsTime = stageTiming?.queryService ? `|${stageTiming.queryService}ms|` : '';

    return `
graph TD
    Client[Client] -->${qsTime} QS[Projection Svc]
    QS --> Local[(Local Projections)]

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
