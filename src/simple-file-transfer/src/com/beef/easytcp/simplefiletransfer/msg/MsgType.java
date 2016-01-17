package com.beef.easytcp.simplefiletransfer.msg;

public enum MsgType {
	FileTransferStartReq,
	FileTransferStartResp,
	
	FileTransferPieceReq,
	FileTransferPieceResp,
	
	FileTransferCompletedReq,
	FileTransferCompletedResp,
}
