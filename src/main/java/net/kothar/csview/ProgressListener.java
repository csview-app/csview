package net.kothar.csview;

public interface ProgressListener {

	void columnsChanged(int columns);

	void completed();

	void changed();

}
