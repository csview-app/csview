package net.kothar.csview.csv;

import java.nio.ByteBuffer;

/**
 * @see http://blog.sarah-happy.ca/2013/01/java-regular-expression-on-byte-array.html
 */
public class ByteCharSequence implements CharSequence {

	private ByteBuffer buffer;

	public ByteCharSequence(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	@Override
	public int length() {
		return buffer.limit();
	}

	@Override
	public char charAt(int index) {
		return (char) (buffer.get(index) & 0xff);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		ByteBuffer subBuffer = buffer.duplicate();
		subBuffer.position(start);
		subBuffer.limit(end);
		return new ByteCharSequence(subBuffer.slice());
	}

}
