package com.beef.easytcp.junittest;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;

import org.junit.Test;

public class TestSkipList {

	@Test
	public void testConcurrentSkipListMap() {
		ConcurrentSkipListMap<Integer, String> listMap = new ConcurrentSkipListMap<Integer, String>();
		
		Random rand = new Random(System.currentTimeMillis());
		int num;
		System.out.println("TestSkipList() put --------------------");
		for(int i = 0; i < 50; i++) {
			num = rand.nextInt(1000);
			
			listMap.put(num, String.valueOf(num));
			System.out.println("val:" + num);
		}
		
		Iterator<String> iterValue = listMap.values().iterator();
	
		System.out.println("TestSkipList() iterator --------------------");
		while(iterValue.hasNext()) {
			System.out.println("val:" + iterValue.next());
		}
		
		System.out.println("TestSkipList() pollFirstEntry --------------------");
		Map.Entry<Integer, String> entry;
		while((entry = listMap.pollFirstEntry()) != null) {
			System.out.println("val:" + entry.getValue());
		}

		System.out.println("mapSize:" + listMap.size() + "------------------");
	}
}
