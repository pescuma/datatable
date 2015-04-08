package org.pescuma.datatable;

import static java.util.Arrays.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class MemoryDataTable implements DataTable {
	
	private final Collection<LineImpl> lines;
	
	public MemoryDataTable() {
		lines = new ArrayList<LineImpl>();
	}
	
	private MemoryDataTable(Collection<LineImpl> lines) {
		this.lines = lines;
	}
	
	@Override
	public int size() {
		return lines.size();
	}
	
	@Override
	public boolean isEmpty() {
		return lines.isEmpty();
	}
	
	@Override
	public Collection<String> getDistinct(int column) {
		// By default sort by the columns text
		Set<String> result = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		
		for (Line line : lines)
			result.add(line.getColumn(column));
		
		return Collections.unmodifiableCollection(result);
	}
	
	@Override
	public Collection<String[]> getDistinct(int... columns) {
		// By default sort by the columns text
		Set<String[]> result = new TreeSet<String[]>(getColumnsComparator());
		
		for (Line line : lines)
			result.add(line.getColumns(columns));
		
		return Collections.unmodifiableCollection(result);
	}
	
	@Override
	public DataTable sumDistinct(int... columns) {
		DataTable result = new MemoryDataTable();
		
		for (Line line : lines)
			result.inc(line.getValue(), line.getColumns(columns));
		
		return result;
	}
	
	private Comparator<String[]> getColumnsComparator() {
		return new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				for (int i = 0; i < o1.length; i++) {
					int comp = o1[i].compareToIgnoreCase(o2[i]);
					if (comp != 0)
						return comp;
				}
				return 0;
			}
		};
	}
	
	@Override
	public void add(double value, String... columns) {
		columns = cleanup(columns);
		lines.add(new LineImpl(value, columns));
	}
	
	@Override
	public void inc(double value, String... columns) {
		Collection<LineImpl> items = getLines(columns);
		if (items.isEmpty())
			add(value, columns);
		else
			items.iterator().next().value += value;
	}
	
	@Override
	public void add(DataTable other) {
		for (Line line : other.getLines())
			lines.add(new LineImpl(line.getValue(), line.getColumns()));
	}
	
	@Override
	public void inc(DataTable other) {
		for (Line line : other.getLines())
			inc(line.getValue(), line.getColumns());
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Collection<Line> getLines() {
		return (Collection) Collections.unmodifiableCollection(lines);
	}
	
	private Collection<LineImpl> getLines(final String... aColumns) {
		final String[] columns = cleanup(aColumns);
		
		return Collections2.filter(lines, new Predicate<LineImpl>() {
			@Override
			public boolean apply(LineImpl line) {
				return Arrays.equals(columns, line.columns);
			}
		});
	}
	
	@Override
	public double get(final String... columns) {
		Collection<LineImpl> filtered = getLines(columns);
		
		int size = filtered.size();
		if (size > 1)
			throw new IllegalArgumentException("More than one line has info: " + Arrays.toString(columns));
		if (size < 1)
			throw new IllegalArgumentException("Line not found: " + Arrays.toString(columns));
		
		return filtered.iterator().next().getValue();
	}
	
	@Override
	public DataTable filter(final String... columns) {
		Collection<LineImpl> filtered = Collections2.filter(lines, new Predicate<LineImpl>() {
			@Override
			public boolean apply(LineImpl line) {
				return line.infoStartsWith(columns);
			}
		});
		return new MemoryDataTable(filtered);
	}
	
	@Override
	public DataTable filter(final int column, final String value) {
		Collection<LineImpl> filtered = Collections2.filter(lines, new Predicate<LineImpl>() {
			@Override
			public boolean apply(LineImpl line) {
				return line.hasInfo(column, value);
			}
		});
		return new MemoryDataTable(filtered);
	}
	
	@Override
	public DataTable filter(final Predicate<Line> predicate) {
		Collection<LineImpl> filtered = Collections2.filter(lines, new Predicate<LineImpl>() {
			@Override
			public boolean apply(LineImpl line) {
				return predicate.apply(line);
			}
		});
		return new MemoryDataTable(filtered);
	}
	
	@Override
	public DataTable filter(final int column, final Predicate<String> predicate) {
		Collection<LineImpl> filtered = Collections2.filter(lines, new Predicate<LineImpl>() {
			@Override
			public boolean apply(LineImpl line) {
				return predicate.apply(line.getColumn(column));
			}
		});
		return new MemoryDataTable(filtered);
	}
	
	@Override
	public DataTable map(final int column, final Function<String, String> transform) {
		Collection<LineImpl> newLines = Collections2.transform(lines, new Function<LineImpl, LineImpl>() {
			@Override
			public LineImpl apply(LineImpl line) {
				String orig = line.getColumn(column);
				String transformed = transform.apply(orig);
				
				if (orig.equals(transformed))
					return line;
				
				String[] columns = Arrays.copyOf(line.columns, Math.max(line.columns.length, column + 1));
				columns[column] = transformed;
				
				if (column + 1 > line.columns.length)
					columns = cleanup(columns);
				
				return new LineImpl(line.value, columns);
			}
		});
		return new MemoryDataTable(newLines);
	}
	
	@Override
	public double sum() {
		double result = 0;
		for (Line line : lines)
			result += line.getValue();
		return result;
	}
	
	@Override
	public Collection<String> getColumn(final int column) {
		return Collections2.transform(lines, new Function<Line, String>() {
			@Override
			public String apply(Line line) {
				return line.getColumn(column);
			}
		});
	}
	
	@Override
	public Collection<String[]> getColumns(final int... columns) {
		return Collections2.transform(lines, new Function<Line, String[]>() {
			@Override
			public String[] apply(Line line) {
				return line.getColumns(columns);
			}
		});
	}
	
	private String[] cleanup(String... info) {
		info = removeEmptyAtEnd(info);
		info = replaceNull(info);
		return info;
	}
	
	private String[] removeEmptyAtEnd(String[] info) {
		int last = info.length - 1;
		for (; last > 0; last--)
			if (info[last] != null && !info[last].isEmpty())
				break;
		last++;
		if (last == info.length)
			return info;
		else
			return copyOf(info, last);
	}
	
	private String[] replaceNull(String[] info) {
		int nullPos = findNull(info);
		if (nullPos < 0)
			return info;
		
		String[] result = Arrays.copyOf(info, info.length);
		for (int i = nullPos; i < result.length; i++)
			if (result[i] == null)
				result[i] = "";
		return result;
	}
	
	private int findNull(String[] info) {
		for (int i = 0; i < info.length; i++)
			if (info[i] == null)
				return i;
		return -1;
	}
	
	private static class LineImpl implements Line {
		
		double value;
		final String[] columns;
		
		LineImpl(double value, String[] columns) {
			this.value = value;
			this.columns = columns;
		}
		
		private boolean equalsIgnoreNull(String a, String b) {
			if (a == null)
				a = "";
			if (b == null)
				b = "";
			return a.equals(b);
		}
		
		boolean hasInfo(int column, String name) {
			if (column >= columns.length)
				return equalsIgnoreNull("", name);
			
			return equalsIgnoreNull(columns[column], name);
		}
		
		boolean infoStartsWith(String[] start) {
			for (int i = 0; i < start.length; i++)
				if (!equalsIgnoreNull(getColumn(i), start[i]))
					return false;
			
			return true;
		}
		
		@Override
		public double getValue() {
			return value;
		}
		
		@Override
		public String getColumn(int column) {
			if (column >= columns.length)
				return "";
			
			return columns[column];
		}
		
		@Override
		public String[] getColumns(int... columnIndexes) {
			if (columnIndexes == null || columnIndexes.length == 0)
				return columns;
			
			String[] result = new String[columnIndexes.length];
			for (int i = 0; i < columnIndexes.length; i++)
				result[i] = getColumn(columnIndexes[i]);
			return result;
		}
		
		@Override
		public String toString() {
			return "LineImpl [" + value + " " + Arrays.toString(columns) + "]";
		}
	}
}
