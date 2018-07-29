package net.kothar.csview.csv;

public class Holder<T> {
	public T value;

	public Holder() {
	}

	public Holder(T value) {
		super();
		this.value = value;
	}

	public T getValue() {
		return value;
	}

	public void setValue(T value) {
		this.value = value;
	}
}
