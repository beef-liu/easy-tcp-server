package com.beef.easytcp;

import java.util.Iterator;
import java.util.LinkedList;

public class MessageList <T> implements Iterable<T> {
	protected LinkedList<T> _backList = new LinkedList<T>();
	
	/*
	public T poll() {
		return _backQueue.poll();
	}
	
	public T peek() {
		return _backQueue.peek();
	}
	*/
	
	public void add(T obj) {
		_backList.add(obj);
	}
	
	public int size() {
		return _backList.size();
	}
	
	public void clear() {
		_backList.clear();
	}

	@Override
	public Iterator<T> iterator() {
		return _backList.iterator();
	}
	
}
