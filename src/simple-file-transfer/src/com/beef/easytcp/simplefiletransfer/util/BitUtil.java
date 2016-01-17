package com.beef.easytcp.simplefiletransfer.util;

public class BitUtil {
	private final static int[] Mask1 = new int[] {
		0x80,
		0x40,
		0x20,
		0x10,
		0x8,
		0x4,
		0x2,
		0x1,
	};
	private final static int[] Mask0 = new int[] {
		0x7f,
		0xbf,
		0xdf,
		0xef,
		0xf7,
		0xfb,
		0xfd,
		0xfe
	};
	
	public static int countBit1(byte b) {
		int bit1Count = 0;
		
		for(int i = 0; i < 8; i++) {
			if(((b & 0xff) & Mask1[i]) != 0) {
				bit1Count ++;
			} 
		}
		
		return bit1Count;
	}
	
	/**
	 * 
	 * @param b
	 * @param bitPos 0~7, 0 is the most left(biggest) bit.
	 * @return
	 */
	public static byte setBit1(byte b, int bitPos) {
		return (byte) ((b & 0xff) | Mask1[bitPos]);
	}
	
	public static byte setBit0(byte b, int bitPos) {
		return (byte) ((b & 0xff) & Mask0[bitPos]);
	}
	
	public static int getBit(byte b, int bitPos) {
		return ((b & 0xff) & Mask1[bitPos]) == 0 ? 0:1;
	}
	
	public static String toBitStr(byte b) {
		StringBuilder sb = new StringBuilder();
		
		for(int i = 0; i < 8; i++) {
			sb.append(Integer.toString(getBit(b, i)));
		}
		
		return sb.toString();
	}
	
}
