import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createColumnHelper } from '@tanstack/react-table';
import { DataTable } from './data-table';
type Item = { tag: string; name: string; uses: number };
const helper = createColumnHelper<Item>();
const columns = [
  helper.accessor('name', { header: 'Name' }),
  helper.accessor('uses', { header: 'Uses' }),
  helper.display({ id: 'actions', header: 'Actions', cell: () => <button>Open</button> }),
];
const items: Item[] = [
  { tag: 'B', name: 'Tripod', uses: 3 },
  { tag: 'A', name: 'Camera', uses: 12 },
  { tag: 'C', name: 'Laptop', uses: 7 },
];
const names = () =>
  screen
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[0].textContent);
describe('DataTable', () => {
  it('names the table and sorts a column both ways from its header', async () => {
    render(<DataTable columns={columns} data={items} caption="Equipment by use" />);
    expect(screen.getByRole('table', { name: 'Equipment by use' })).toBeInTheDocument();
    expect(names()).toEqual(['Tripod', 'Camera', 'Laptop']);
    const header = screen.getByRole('columnheader', { name: 'Name' });
    expect(header).toHaveAttribute('aria-sort', 'none');
    await userEvent.click(within(header).getByRole('button'));
    expect(names()).toEqual(['Camera', 'Laptop', 'Tripod']);
    expect(header).toHaveAttribute('aria-sort', 'ascending');
    await userEvent.click(within(header).getByRole('button'));
    expect(names()).toEqual(['Tripod', 'Laptop', 'Camera']);
    expect(header).toHaveAttribute('aria-sort', 'descending');
  });
  it('does not offer sorting on a column without data', () => {
    render(<DataTable columns={columns} data={items} caption="Equipment" />);
    const header = screen.getByRole('columnheader', { name: 'Actions' });
    expect(within(header).queryByRole('button')).not.toBeInTheDocument();
    expect(header).not.toHaveAttribute('aria-sort');
  });
  it('pages its own rows and disables the buttons at both ends', async () => {
    render(<DataTable columns={columns} data={items} caption="Equipment" pageSize={2} />);
    expect(names()).toEqual(['Tripod', 'Camera']);
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(names()).toEqual(['Laptop']);
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });
  it('asks the caller for another page when the API pages the data', async () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={items}
        caption="Equipment"
        paging={{ page: 1, pageCount: 4, onPageChange }}
      />,
    );
    expect(screen.getByText('Page 2 of 4')).toBeInTheDocument();
    // Sorting one page of a longer list would mislead, so headers are plain text.
    expect(
      within(screen.getByRole('columnheader', { name: 'Name' })).queryByRole('button'),
    ).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(onPageChange).toHaveBeenCalledWith(2);
    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(onPageChange).toHaveBeenLastCalledWith(0);
  });
  it('shows the empty message instead of an empty table', () => {
    render(
      <DataTable columns={columns} data={[]} caption="Equipment" empty={<p>No equipment</p>} />,
    );
    expect(screen.getByText('No equipment')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
  it('announces loading and keeps the column headers', () => {
    render(<DataTable columns={columns} data={[]} caption="Equipment" loading />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading');
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('columnheader', { name: 'Uses' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });
});
