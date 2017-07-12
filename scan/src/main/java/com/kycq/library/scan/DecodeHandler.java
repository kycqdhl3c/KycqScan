package com.kycq.library.scan;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;

class DecodeHandler extends Handler {
	static final int DECODE = 1;
	static final int QUIT = 2;
	
	private CaptureHandler captureHandler;
	private MultiFormatReader multiFormatReader;
	private boolean isRunning = true;
	
	DecodeHandler(CaptureHandler captureHandler, MultiFormatReader multiFormatReader) {
		this.captureHandler = captureHandler;
		this.multiFormatReader = multiFormatReader;
	}
	
	@Override
	public void handleMessage(Message message) {
		if (!isRunning) {
			return;
		}
		switch (message.what) {
			case DECODE:
				DecodeInfo decodeInfo = (DecodeInfo) message.obj;
				rotateData(decodeInfo);
				decode(decodeInfo);
				break;
			case QUIT:
				isRunning = false;
				Looper looper = Looper.myLooper();
				if (looper != null) {
					looper.quit();
				}
				break;
		}
	}
	
	@SuppressWarnings("SuspiciousNameCombination")
	private void rotateData(DecodeInfo decodeInfo) {
		byte[] resultData = new byte[decodeInfo.decodeData.length];
		switch (decodeInfo.rotationAngle) {
			case 90: {
				for (int y = 0; y < decodeInfo.decodeHeight; y++) {
					for (int x = 0; x < decodeInfo.decodeWidth; x++) {
						resultData[x * decodeInfo.decodeHeight + decodeInfo.decodeHeight - y - 1]
								= decodeInfo.decodeData[x + y * decodeInfo.decodeWidth];
					}
				}
				int tempSize = decodeInfo.decodeWidth;
				decodeInfo.decodeWidth = decodeInfo.decodeHeight;
				decodeInfo.decodeHeight = tempSize;
				break;
			}
			case 180: {
				int length = decodeInfo.decodeWidth * decodeInfo.decodeHeight;
				for (int index = 0; index < length; index++) {
					resultData[index] = decodeInfo.decodeData[length - index - 1];
				}
				break;
			}
			case 270: {
				for (int y = 0; y < decodeInfo.decodeHeight; y++) {
					for (int x = 0; x < decodeInfo.decodeWidth; x++) {
						resultData[(decodeInfo.decodeWidth - x - 1) * decodeInfo.decodeHeight + y]
								= decodeInfo.decodeData[x + y * decodeInfo.decodeWidth];
					}
				}
				int tempSize = decodeInfo.decodeWidth;
				decodeInfo.decodeWidth = decodeInfo.decodeHeight;
				decodeInfo.decodeHeight = tempSize;
				break;
			}
		}
		decodeInfo.decodeData = resultData;
	}
	
	private void decode(DecodeInfo decodeInfo) {
		Result rawResult = null;
		PlanarYUVLuminanceSource source = buildLuminanceSource(decodeInfo.decodeData,
				decodeInfo.decodeWidth, decodeInfo.decodeHeight);
		if (source != null) {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			try {
				rawResult = this.multiFormatReader.decodeWithState(bitmap);
			} catch (ReaderException ignored) {
			} finally {
				this.multiFormatReader.reset();
			}
		}
		
		if (this.captureHandler == null) {
			return;
		}
		
		if (rawResult != null) {
			Message message = Message.obtain(captureHandler, CaptureHandler.DECODE_SUCCESS, rawResult);
			Bundle bundle = new Bundle();
			bundleThumbnail(source, bundle);
			message.setData(bundle);
			message.sendToTarget();
		} else {
			Message message = Message.obtain(captureHandler, CaptureHandler.DECODE_FAILURE);
			message.sendToTarget();
		}
	}
	
	private PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int dataWidth, int dataHeight) {
		Rect rect = this.captureHandler.getPreviewRect();
		if (rect == null) {
			return null;
		}
		if (rect.left + rect.width() <= dataWidth && rect.top + rect.height() <= dataHeight) {
			return new PlanarYUVLuminanceSource(
					data, dataWidth, dataHeight,
					rect.left, rect.top, rect.width(), rect.height(), false);
		} else if (rect.left + rect.width() <= dataHeight && rect.top + rect.height() <= dataWidth) {
			return new PlanarYUVLuminanceSource(
					data, dataHeight, dataWidth,
					rect.left, rect.top, rect.width(), rect.height(), false);
		}
		return null;
	}
	
	private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
		int[] pixels = source.renderThumbnail();
		int width = source.getThumbnailWidth();
		int height = source.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
		bundle.putByteArray(CaptureHandler.BARCODE_BITMAP, out.toByteArray());
		bundle.putFloat(CaptureHandler.BARCODE_SCALED_FACTOR, (float) width / source.getWidth());
	}
	
	static class DecodeInfo {
		int rotationAngle;
		int decodeWidth;
		int decodeHeight;
		byte[] decodeData;
	}
}
