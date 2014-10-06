package com.beef.easytcp.base;

import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

public class SocketChannelUtil {
	private final static Logger logger = Logger.getLogger(SocketChannelUtil.class);
	
	public static boolean clearSelectionKey(
			SelectionKey selectionKey) {
		try {
			if(selectionKey != null && selectionKey.isValid()) {
				try {
					if(ServerSocketChannel.class.isAssignableFrom(selectionKey.channel().getClass())) {
						logger.debug("ServerSocketChannel closing");
						closeServerSocketChannel((ServerSocketChannel) selectionKey.channel());
					} else {
						closeSocketChannel((SocketChannel) selectionKey.channel());
					}
				} finally {
					try {
						selectionKey.cancel();
					} catch(Throwable e) {
						logger.error("clearSelectionKey()", e);
					}
				}

				return true;
			} else {
				return false;
			}
		} catch(Throwable e) {
			logger.error("clearSelectionKey()", e);
			return false;
		}
	}
	
	protected static void closeServerSocketChannel(ServerSocketChannel socketChannel) {
		try {
			if(!socketChannel.socket().isClosed()) {
				socketChannel.socket().close();
			}
		} catch(Throwable e) {
			logger.error(null, e);
		}

		try {
			socketChannel.close();
		} catch(Throwable e) {
			logger.error(null, e);
		}
	}

	protected static void closeSocketChannel(SocketChannel socketChannel) {
		try {
			if(!socketChannel.socket().isInputShutdown()) {
				socketChannel.socket().shutdownInput();
			}
		} catch(Throwable e) {
			//logger.info(e);
		}
		
		try {
			if(!socketChannel.socket().isOutputShutdown()) {
				socketChannel.socket().shutdownOutput();
			}
		} catch(Throwable e) {
			//mostly client disconnected
			//logger.error(null, e);
		}
		
		try {
			if(!socketChannel.socket().isClosed()) {
				socketChannel.socket().close();
			}
		} catch(Throwable e) {
			logger.error(null, e);
		}

		try {
			socketChannel.close();
		} catch(Throwable e) {
			logger.error(null, e);
		}
		
		//logger.debug("closeSocketChannel:".concat(socketChannel.socket().getRemoteSocketAddress().toString()));
	}

    public static boolean isConnected(Socket socket) {
		return socket != null && socket.isBound() && !socket.isClosed()
			&& socket.isConnected() && !socket.isInputShutdown()
			&& !socket.isOutputShutdown();
    }
	
}
