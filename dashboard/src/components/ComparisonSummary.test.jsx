import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ComparisonSummary from './ComparisonSummary';

describe('ComparisonSummary', () => {
  it('renders the tradeoff comparison title', () => {
    render(<ComparisonSummary />);
    expect(screen.getByText('Tradeoff Comparison')).toBeInTheDocument();
  });

  it('displays latency values when provided', () => {
    render(
      <ComparisonSummary
        federationLatency={150}
        kafkaLatency={50}
        kafkaFreshness="2s ago"
      />
    );

    expect(screen.getByText('150ms')).toBeInTheDocument();
    expect(screen.getByText('50ms')).toBeInTheDocument();
  });

  it('shows dash when no latency values provided', () => {
    render(<ComparisonSummary />);

    const dashes = screen.getAllByText('-');
    expect(dashes.length).toBeGreaterThanOrEqual(2);
  });

  it('calculates and displays latency difference', () => {
    render(
      <ComparisonSummary
        federationLatency={200}
        kafkaLatency={100}
        kafkaFreshness="1s ago"
      />
    );

    // 200 - 100 = 100, 100/100 * 100 = 100%
    expect(screen.getByText('100%')).toBeInTheDocument();
    // Check for the specific comparison text (not tooltip text)
    expect(screen.getByText(/Event-Driven is.*faster/)).toBeInTheDocument();
  });

  it('displays Kafka freshness value', () => {
    render(
      <ComparisonSummary
        federationLatency={100}
        kafkaLatency={50}
        kafkaFreshness="5s ago"
      />
    );

    expect(screen.getByText('5s ago')).toBeInTheDocument();
  });

  it('shows consistency comparison labels', () => {
    render(<ComparisonSummary />);

    expect(screen.getByText('Data Consistency')).toBeInTheDocument();
    expect(screen.getByText('Real-time')).toBeInTheDocument();
    expect(screen.getByText('Eventually consistent with lag indicator')).toBeInTheDocument();
  });

  it('shows failure mode comparison', () => {
    render(<ComparisonSummary />);

    expect(screen.getByText('Failure Mode')).toBeInTheDocument();
    expect(screen.getByText('1 service down = query fails')).toBeInTheDocument();
    expect(screen.getByText('Query works (stale data)')).toBeInTheDocument();
  });

  it('displays key insight section', () => {
    render(<ComparisonSummary />);

    expect(screen.getByText(/Key Insight:/)).toBeInTheDocument();
    expect(screen.getByText(/Federation provides real-time data consistency/)).toBeInTheDocument();
  });
});
