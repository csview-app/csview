package net.kothar.csview;

public interface ProgressListener {

	void changed(long progress);
	void completed();

}
