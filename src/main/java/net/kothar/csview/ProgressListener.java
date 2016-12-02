package net.kothar.csview;

public interface ProgressListener {

	void columnsChanged(int columns);
	void changed(long progress);
	void completed();

}
