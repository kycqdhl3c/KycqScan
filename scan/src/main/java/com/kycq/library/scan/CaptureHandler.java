package com.kycq.library.scan;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;

import java.util.concurrent.CountDownLatch;

public class CaptureHandler extends Handler {
	static final String BARCODE_BITMAP = "barcode_bitmap";
	static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";
	
	private static final int DECODE_RESTART = 1;
	static final int DECODE_SUCCESS = 2;
	static final int DECODE_FAILURE = 3;
	
	private static int STATE_PREVIEW = 1;
	private static int STATE_SUCCESS = 2;
	private static int STATE_DONE = 3;
	
	private ScanView scanView;
	private CameraManager cameraManager;
	
	private final InitLatchThread initLatchThread;
	private int state;
	
	CaptureHandler(ScanView scanView,CameraManager cameraManager) {
		this.scanView = scanView;
		this.cameraManager = cameraManager;
		
		this.initLatchThread = new InitLatchThread(this.scanView.getMultiFormatReader());
		this.initLatchThread.start();
		this.state = STATE_SUCCESS;
		
		this.cameraManager.startPreview();
		restartPreviewAndDecode();
	}
	
	void restartPreviewAndDecode() {
		if (this.state == STATE_SUCCESS) {
			this.state = STATE_PREVIEW;
			this.cameraManager.requestPreviewFrame(
					this.initLatchThread.getDecodeHandler(),
					DecodeHandler.DECODE
			);
			this.scanView.restartFinder();
		}
	}
	
	void quitSynchronously() {
		this.state = STATE_DONE;
		this.cameraManager.stopPreview();
		Message quit = Message.obtain(this.initLatchThread.getDecodeHandler(), DecodeHandler.QUIT);
		quit.sendToTarget();
		try {
			this.initLatchThread.join(500L);
		} catch (InterruptedException ignored) {
		}
		
		removeMessages(DECODE_SUCCESS);
		removeMessages(DECODE_FAILURE);
	}
	
	Rect getPreviewRect() {
		return this.scanView.getPreviewRect();
	}
	
	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
			case DECODE_RESTART:
				restartPreviewAndDecode();
				break;
			case DECODE_SUCCESS:
				this.state = STATE_SUCCESS;
				Bundle bundle = message.getData();
				Bitmap barcodeBitmap = null;
				float scaleFactor = 1.0f;
				if (bundle != null) {
					byte[] compressedBitmap = bundle.getByteArray(BARCODE_BITMAP);
					if (compressedBitmap != null) {
						barcodeBitmap = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
						barcodeBitmap = barcodeBitmap.copy(Bitmap.Config.ARGB_8888, true);
					}
					scaleFactor = bundle.getFloat(BARCODE_SCALED_FACTOR);
				}
				this.scanView.decodeSuccess(((Result) message.obj).getText(), barcodeBitmap, scaleFactor);
				break;
			case DECODE_FAILURE:
				this.state = STATE_PREVIEW;
				this.cameraManager.requestPreviewFrame(
						this.initLatchThread.getDecodeHandler(),
						DecodeHandler.DECODE
				);
				break;
		}
	}
	
	/**
	 * 初始化异步线程消息队列
	 */
	private class InitLatchThread extends Thread {
		private final CountDownLatch handlerInitLatch;
		
		private MultiFormatReader multiFormatReader;
		private DecodeHandler decodeHandler;
		
		InitLatchThread(MultiFormatReader multiFormatReader) {
			this.handlerInitLatch = new CountDownLatch(1);
			this.multiFormatReader = multiFormatReader;
		}
		
		DecodeHandler getDecodeHandler() {
			try {
				this.handlerInitLatch.await();
			} catch (InterruptedException ignored) {
			}
			return this.decodeHandler;
		}
		
		@Override
		public void run() {
			Looper.prepare();
			this.decodeHandler = new DecodeHandler(CaptureHandler.this, this.multiFormatReader);
			this.handlerInitLatch.countDown();
			Looper.loop();
		}
	}
}
