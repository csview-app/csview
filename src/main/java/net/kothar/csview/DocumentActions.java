package net.kothar.csview;

public interface DocumentActions {

	boolean close();
	
	boolean copySelection();
	
	void toggleSearch();

	void gotoCell();
}
