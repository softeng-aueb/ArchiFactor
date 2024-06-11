package gr.aueb.java.util;

import java.util.Objects;

/**
 * A simple record with two entries
 * @param <K>
 * @param <V>
 */
public class Pair<K, V> {
	
	public final K first;
	public final V second;
	
	public Pair(K first, V second) {
		super();
		this.first = first;
		this.second = second;
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pair other = (Pair) obj;
		
		if (!first.getClass().equals(other.first.getClass()) ||
				!second.getClass().equals(other.second.getClass())) {
			return false;
		}
		return Objects.equals(first, other.first) && Objects.equals(second, other.second);
	}
	
	
	
}
