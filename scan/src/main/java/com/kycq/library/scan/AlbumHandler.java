package com.kycq.library.scan;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.File;
import java.util.concurrent.CountDownLatch;

class AlbumHandler extends Handler {
	private static final int SUCCESS = 1;
	private static final int FAILURE = 2;
	
	private ScanView scanView;
	private DecodeThread decodeThread;
	
	AlbumHandler(ScanView scanView) {
		this.scanView = scanView;
		
		this.decodeThread = new DecodeThread(this, scanView.getMultiFormatReader());
		this.decodeThread.start();
	}
	
	void decode(File file) {
		try {
			this.decodeThread.handlerInitLatch.await();
		} catch (InterruptedException ignored) {
		}
		Message message = new Message();
		message.obj = file;
		this.decodeThread.decodeHandler.sendMessage(message);
	}
	
	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
			case SUCCESS:
				this.scanView.decodeSuccess(((Result) message.obj).getText(), null, 0);
				break;
			case FAILURE:
				this.scanView.decodeFailure();
				break;
		}
	}
	
	private class DecodeThread extends Thread {
		private final CountDownLatch handlerInitLatch;
		private AlbumHandler albumHandler;
		private MultiFormatReader multiFormatReader;
		private DecodeHandler decodeHandler;
		
		DecodeThread(AlbumHandler albumHandler, MultiFormatReader multiFormatReader) {
			this.handlerInitLatch = new CountDownLatch(1);
			this.albumHandler = albumHandler;
			this.multiFormatReader = multiFormatReader;
		}
		
		@Override
		public void run() {
			Looper.prepare();
			this.decodeHandler = new DecodeHandler(this.albumHandler, this.multiFormatReader);
			this.handlerInitLatch.countDown();
			Looper.loop();
		}
	}
	
	private static class DecodeHandler extends Handler {
		private AlbumHandler albumHandler;
		private MultiFormatReader multiFormatReader;
		
		DecodeHandler(AlbumHandler albumHandler, MultiFormatReader multiFormatReader) {
			this.albumHandler = albumHandler;
			this.multiFormatReader = multiFormatReader;
		}
		
		@Override
		public void handleMessage(Message message) {
			File file = (File) message.obj;
			Result rawResult = null;
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(file.getPath(), options);
				int sampleSize = (int) (options.outHeight / (float) 200);
				
				if (sampleSize <= 0) {
					sampleSize = 1;
				}
				options.inSampleSize = sampleSize;
				options.inJustDecodeBounds = false;
				Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
				
				int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
				bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
				RGBLuminanceSource rgbLuminanceSource = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels);
				rawResult = this.multiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(rgbLuminanceSource)));
			} catch (Exception ignored) {
			} catch (OutOfMemoryError error) {
				System.gc();
			} finally {
				this.multiFormatReader.reset();
			}
			
			if (rawResult != null) {
				Message messageResult = Message.obtain(this.albumHandler, SUCCESS, rawResult);
				messageResult.sendToTarget();
			} else {
				Message messageResult = Message.obtain(this.albumHandler, FAILURE);
				messageResult.sendToTarget();
			}
		}
	}
}
