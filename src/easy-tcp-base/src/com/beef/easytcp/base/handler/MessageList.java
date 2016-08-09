package com.beef.easytcp.base.handler;

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
	
	public MessageList<T> clone() {
		MessageList<T> newList = new MessageList<T>();
		newList._backList.addAll(_backList);
		
		return newList;
	}
	
	public void add(T obj) {
		_backList.add(obj);
	}
	
	public void addAll(MessageList<T> msgs) {
		_backList.addAll(msgs._backList);
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
	
	public static <T> MessageList<T> wrap(T t) {
		MessageList<T> msgList = new MessageList<T>();
		msgList.add(t);
		
		return msgList;
	}
	
}
