package com.mpatric.mp3agic;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

public class EncodedText {
	
	public static final byte TEXT_ENCODING_ISO_8859_1 = 0;
	public static final byte TEXT_ENCODING_UTF_16 = 1;
	public static final byte TEXT_ENCODING_UTF_16BE = 2;
	public static final byte TEXT_ENCODING_UTF_8 = 3;
	
	public static final String CHARSET_ISO_8859_1 = "ISO-8859-1";
	public static final String CHARSET_UTF_16 = "UTF-16LE";
	public static final String CHARSET_UTF_16BE = "UTF-16BE";
	public static final String CHARSET_UTF_8 = "UTF-8";
	
	private static final String[] characterSets = {
		CHARSET_ISO_8859_1,
		CHARSET_UTF_16,
		CHARSET_UTF_16BE,
		CHARSET_UTF_8
	};

	private static final byte[][] boms = {
		{},
		{(byte)0xff, (byte)0xfe},
		{(byte) 0xfe, (byte) 0xff},
		{}
	};
	
	private static final byte[][] terminators = {
		{0},
		{0, 0},
		{0, 0},
		{0}
	};
	
	private byte[] value;
	private byte textEncoding;
	
	public EncodedText(byte textEncoding, byte[] value) {
		this.textEncoding = textEncoding;
		this.value = value;
		this.stripBomAndTerminator();
	}
	
	public EncodedText(byte textEncoding, String string) {
		this.textEncoding = textEncoding;
		value = stringToUnicodeBytes(string, characterSetForTextEncoding(textEncoding));
		this.stripBomAndTerminator();
	}
	
	public EncodedText(byte[] value) {
		this(textEncodingForBytesFromBOM(value), value);
	}
	
	private static byte textEncodingForBytesFromBOM(byte[] value) {
		if (value.length >= 2 && value[0] == (byte)0xff && value[1] == (byte)0xfe) {
			return TEXT_ENCODING_UTF_16;
		} else if (value.length >= 2 && value[0] == (byte)0xfe && value[1] == (byte)0xff) {
			return TEXT_ENCODING_UTF_16BE;
		} else if (value.length >= 3 && (value[0] == (byte)0xef && value[1] == (byte)0xbb && value[2] == (byte)0xbf)) {
			return TEXT_ENCODING_UTF_8;
		} else {
			return TEXT_ENCODING_ISO_8859_1;
		}
	}
	
	private String characterSetForTextEncoding(byte textEncoding) {
		try {
			return characterSets[textEncoding];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid text encoding " + textEncoding);
		}
	}
	
	private void stripBomAndTerminator() {
		int leadingCharsToRemove = 0;
		if (value.length >= 2 && ((value[0] == (byte)0xfe && value[1] == (byte)0xff) || (value[0] == (byte)0xff && value[1] == (byte)0xfe))) {
			leadingCharsToRemove = 2;
		} else if (value.length >= 3 && (value[0] == (byte)0xef && value[1] == (byte)0xbb && value[2] == (byte)0xbf)) {
			leadingCharsToRemove = 3;
		}
		int trailingCharsToRemove = 0;
		for (int i = 1; i <= 2; i++) {
			if ((value.length - leadingCharsToRemove - trailingCharsToRemove) > i && value[value.length - i] == 0) {
				trailingCharsToRemove++;
			} else {
				break;
			}
		}
		if (leadingCharsToRemove + trailingCharsToRemove > 0) {
			byte[] newValue = new byte[value.length - leadingCharsToRemove - trailingCharsToRemove];
			System.arraycopy(value, leadingCharsToRemove, newValue, 0, newValue.length);
			value = newValue;
		}
	}
	
	public byte getTextEncoding() {
		return textEncoding;
	}

	public void setTextEncoding(byte textEncoding) {
		this.textEncoding = textEncoding;
	}

	public byte[] toBytes() {
		return toBytes(false, false);
	}
	
	public byte[] toBytes(boolean includeBom) {
		return toBytes(includeBom, false);
	}
	
	public byte[] toBytes(boolean includeBom, boolean includeTerminator) {
		characterSetForTextEncoding(textEncoding); // ensured textEncoding is valid
		int newLength = value.length + (includeBom ? boms[textEncoding].length : 0) + (includeTerminator ? terminators[textEncoding].length : 0);
		if (newLength == value.length) {
			return value;
		} else {
			byte bytes[] = new byte[newLength];
			int i = 0;
			if (includeBom) {
				System.arraycopy(boms[textEncoding], 0, bytes, i, boms[textEncoding].length);
				i += boms[textEncoding].length;
			}
			System.arraycopy(value, 0, bytes, i, value.length);
			i += value.length;
			if (includeTerminator) {
				System.arraycopy(terminators[textEncoding], 0, bytes, i, terminators[textEncoding].length);
			}
			return bytes;
		}
	}
	
	public String toString() {
		return unicodeBytesToString(value, characterSetForTextEncoding(textEncoding));
	}

	public String getCharacterSet() {
		return characterSetForTextEncoding(textEncoding);
	}
	
	public boolean equals(Object obj) {
		if (! (obj instanceof EncodedText)) return false;
		if (super.equals(obj)) return true;
		EncodedText other = (EncodedText) obj;
		if (textEncoding != other.textEncoding) return false;
		if (! Arrays.equals(value, other.value)) return false;
		return true;
	}
	
	public static String unicodeBytesToString(byte[] bytes, String characterSet) {
		Charset charset = Charset.forName(characterSet);
		CharsetDecoder decoder = charset.newDecoder();
		try {
			CharBuffer cbuf = decoder.decode(ByteBuffer.wrap(bytes));
			String s = cbuf.toString();
			int length = s.indexOf(0);
			if (length == -1) return s;
			return s.substring(0, length);
		} catch (CharacterCodingException e) {
			return null;
		}
	}
	
	public static byte[] stringToUnicodeBytes(String s, String characterSet) {
		Charset charset = Charset.forName(characterSet);
		CharsetEncoder encoder = charset.newEncoder();
		ByteBuffer byteBuffer;
		try {
			byteBuffer = encoder.encode(CharBuffer.wrap(s));
			return BufferTools.copyBuffer(byteBuffer.array(), 0, byteBuffer.limit());
		} catch (CharacterCodingException e) {
			return null;
		}
	}
}