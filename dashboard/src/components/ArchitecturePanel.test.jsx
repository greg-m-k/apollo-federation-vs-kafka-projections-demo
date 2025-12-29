import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ArchitecturePanel from './ArchitecturePanel';

// Mock ArchitectureDiagram since it uses Mermaid
vi.mock('./ArchitectureDiagram', () => ({
  default: ({ type }) => <div data-testid="architecture-diagram">{type} diagram</div>
}));

describe('ArchitecturePanel', () => {
  const defaultFederationMetrics = {
    latency: null,
    servicesUp: { hr: false, employment: false, security: false },
    lastQuery: null,
    queryCount: 0,
    errorCount: 0
  };

  const defaultKafkaMetrics = {
    latency: null,
    servicesUp: { query: false, consumer: false, kafka: false },
    lastQuery: null,
    queryCount: 0,
    dataFreshness: 'N/A',
    consumerLag: 0
  };

  describe('Federation Panel', () => {
    it('renders federation title and description', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('GraphQL Federation')).toBeInTheDocument();
      expect(screen.getByText('Synchronous composition, real-time data')).toBeInTheDocument();
    });

    it('displays federation service statuses', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={{ ...defaultFederationMetrics, servicesUp: { hr: true, employment: true, security: false } }}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('HR')).toBeInTheDocument();
      expect(screen.getByText('Employment')).toBeInTheDocument();
      expect(screen.getByText('Security')).toBeInTheDocument();
    });

    it('displays latency when provided', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={{ ...defaultFederationMetrics, latency: 150 }}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('150ms')).toBeInTheDocument();
    });

    it('shows services called metric for federation', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Services Called')).toBeInTheDocument();
      expect(screen.getByText('3')).toBeInTheDocument();
      expect(screen.getByText('per query')).toBeInTheDocument();
    });

    it('calls onQuery when button clicked', () => {
      const onQuery = vi.fn();
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={onQuery}
        />
      );

      fireEvent.click(screen.getByText('Query Composed View'));
      expect(onQuery).toHaveBeenCalledTimes(1);
    });
  });

  describe('Kafka Projections Panel', () => {
    it('renders Kafka Projections title and description', () => {
      render(
        <ArchitecturePanel
          title="Kafka Projections"
          type="kafka"
          metrics={defaultKafkaMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Kafka Projections')).toBeInTheDocument();
      expect(screen.getByText('Asynchronous events, local projections')).toBeInTheDocument();
    });

    it('displays Kafka service statuses', () => {
      render(
        <ArchitecturePanel
          title="Kafka Projections"
          type="kafka"
          metrics={defaultKafkaMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Projection')).toBeInTheDocument();
      expect(screen.getByText('Consumer')).toBeInTheDocument();
      expect(screen.getByText('Kafka')).toBeInTheDocument();
    });

    it('shows data freshness metric for Kafka', () => {
      render(
        <ArchitecturePanel
          title="Kafka Projections"
          type="kafka"
          metrics={{ ...defaultKafkaMetrics, dataFreshness: '2s ago' }}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Data Freshness')).toBeInTheDocument();
      expect(screen.getByText('2s ago')).toBeInTheDocument();
    });

    it('calls onQuery when Kafka button clicked', () => {
      const onQuery = vi.fn();
      render(
        <ArchitecturePanel
          title="Kafka Projections"
          type="kafka"
          metrics={defaultKafkaMetrics}
          logs={[]}
          onQuery={onQuery}
        />
      );

      fireEvent.click(screen.getByText('Query Local Projection'));
      expect(onQuery).toHaveBeenCalledTimes(1);
    });
  });

  describe('Query Results', () => {
    it('displays last query result when provided', () => {
      const lastQuery = { data: { person: { name: 'Alice' } } };
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={{ ...defaultFederationMetrics, lastQuery }}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Last Query Result')).toBeInTheDocument();
      expect(screen.getByText(/Alice/)).toBeInTheDocument();
    });

    it('does not show query result section when no query', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.queryByText('Last Query Result')).not.toBeInTheDocument();
    });
  });

  describe('Activity Logs', () => {
    it('displays log entries', () => {
      const logs = [
        { timestamp: '10:00:00', message: 'Query started' },
        { timestamp: '10:00:01', message: 'Success: 50ms' }
      ];
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={logs}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('Activity Log')).toBeInTheDocument();
      expect(screen.getByText('10:00:00')).toBeInTheDocument();
      expect(screen.getByText(/Query started/)).toBeInTheDocument();
      expect(screen.getByText(/Success: 50ms/)).toBeInTheDocument();
    });

    it('shows empty state when no logs', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByText('No activity yet...')).toBeInTheDocument();
    });
  });

  describe('Architecture Diagram', () => {
    it('renders architecture diagram component', () => {
      render(
        <ArchitecturePanel
          title="GraphQL Federation"
          type="federation"
          metrics={defaultFederationMetrics}
          logs={[]}
          onQuery={() => {}}
        />
      );

      expect(screen.getByTestId('architecture-diagram')).toBeInTheDocument();
      expect(screen.getByText('federation diagram')).toBeInTheDocument();
    });
  });
});
