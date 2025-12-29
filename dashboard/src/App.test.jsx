import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import App from './App';

// Mock child components to isolate App testing
vi.mock('./components/ArchitecturePanel', () => ({
  default: ({ title, type, onQuery }) => (
    <div data-testid={`panel-${type}`}>
      <span>{title}</span>
      <button onClick={onQuery}>Query {type}</button>
    </div>
  )
}));

vi.mock('./components/ComparisonSummary', () => ({
  default: ({ federationLatency, kafkaLatency, kafkaFreshness }) => (
    <div data-testid="comparison-summary">
      <span>Fed: {federationLatency || '-'}</span>
      <span>Kafka: {kafkaLatency || '-'}</span>
      <span>Freshness: {kafkaFreshness || 'N/A'}</span>
    </div>
  )
}));

// Mock persons data returned by the API
const mockPersons = [
  { id: 'person-001', name: 'Alice Johnson' },
  { id: 'person-002', name: 'Bob Smith' },
  { id: 'person-003', name: 'Carol Williams' },
  { id: 'person-005', name: 'Eva Martinez' }
];

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default mock for persons fetch
    global.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockPersons),
      headers: { get: () => null }
    });
  });

  it('renders the header', async () => {
    render(<App />);

    expect(screen.getByText('Architecture Comparison Dashboard')).toBeInTheDocument();
    expect(screen.getByText('GraphQL Federation vs Kafka Projections')).toBeInTheDocument();
  });

  it('renders person selector with dynamic options', async () => {
    render(<App />);

    // Wait for persons to load
    await waitFor(() => {
      expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
    });

    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();
    expect(select.value).toBe('person-001');

    expect(screen.getByText('Bob Smith (person-002)')).toBeInTheDocument();
    expect(screen.getByText('Carol Williams (person-003)')).toBeInTheDocument();
    expect(screen.getByText('Eva Martinez (person-005)')).toBeInTheDocument();
  });

  it('shows fallback option when persons fail to load', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

    render(<App />);

    // Wait for loading to complete
    await waitFor(() => {
      expect(screen.getByText('person-001 (default)')).toBeInTheDocument();
    });
  });

  it('renders both architecture panels', () => {
    render(<App />);

    expect(screen.getByTestId('panel-federation')).toBeInTheDocument();
    expect(screen.getByTestId('panel-kafka')).toBeInTheDocument();
    expect(screen.getByText('GraphQL Federation')).toBeInTheDocument();
    expect(screen.getByText('Kafka Projections')).toBeInTheDocument();
  });

  it('renders comparison summary', () => {
    render(<App />);

    expect(screen.getByTestId('comparison-summary')).toBeInTheDocument();
  });

  it('renders control buttons', () => {
    render(<App />);

    expect(screen.getByText('Query Both Architectures')).toBeInTheDocument();
    expect(screen.getByText('Create Person')).toBeInTheDocument();
  });

  it('allows changing selected person', async () => {
    render(<App />);

    // Wait for persons to load
    await waitFor(() => {
      expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
    });

    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: 'person-002' } });

    expect(select.value).toBe('person-002');
  });

  describe('Federation Query', () => {
    it('queries federation endpoint on button click', async () => {
      global.fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve(mockPersons), headers: { get: () => null } }) // persons fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve({ data: { person: { name: 'Alice' } } }) }); // federation query

      render(<App />);

      // Wait for initial load
      await waitFor(() => {
        expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Query federation'));

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          'http://localhost:4000',
          expect.objectContaining({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
          })
        );
      });
    });

    it('handles federation query errors gracefully', async () => {
      global.fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve(mockPersons), headers: { get: () => null } })
        .mockRejectedValueOnce(new Error('Network error'));

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Query federation'));

      // Should not throw, error is handled internally
      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('Kafka Query', () => {
    it('queries Kafka endpoint on button click', async () => {
      global.fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve(mockPersons), headers: { get: () => null } })
        .mockResolvedValueOnce({
          json: () => Promise.resolve({ person: { name: 'Alice' }, freshness: { dataFreshness: '2s ago' } }),
          headers: { get: () => null }
        });

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Query kafka'));

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          'http://localhost:8090/api/composed/person-001'
        );
      });
    });

    it('handles Kafka query errors gracefully', async () => {
      global.fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve(mockPersons), headers: { get: () => null } })
        .mockRejectedValueOnce(new Error('Network error'));

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Query kafka'));

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe('Query Both', () => {
    it('queries both architectures when button clicked', async () => {
      global.fetch
        .mockResolvedValueOnce({ json: () => Promise.resolve(mockPersons), headers: { get: () => null } }) // persons
        .mockResolvedValueOnce({ json: () => Promise.resolve({ data: { person: { name: 'Alice' } } }) }) // federation
        .mockResolvedValueOnce({
          json: () => Promise.resolve({ person: { name: 'Alice' } }),
          headers: { get: () => null }
        }); // kafka

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText('Alice Johnson (person-001)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Query Both Architectures'));

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(3); // persons + federation + kafka
      });
    });
  });
});
