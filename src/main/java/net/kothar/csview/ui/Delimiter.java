package net.kothar.csview.ui;

public enum Delimiter {
	COMMA(','),
	TAB('\t'),
	PIPE('|'),
	COLON(':'),
	SEMICOLON(';');
	
	public final char character;

	private Delimiter(char character) {
		this.character = character;
	}
}
