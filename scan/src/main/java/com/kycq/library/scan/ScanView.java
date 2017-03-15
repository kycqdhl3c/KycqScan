package com.kycq.library.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class ScanView extends FrameLayout implements SurfaceHolder.Callback {
	private SurfaceView previewView;
	private FinderView finderView;
	
	private boolean isSurfaced;
	private CameraManager cameraManager;
	
	private CaptureHandler captureHandler;
	
	private MultiFormatReader multiFormatReader;
	
	private OnScanListener onScanListener;
	
	public ScanView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		this.previewView = new SurfaceView(context);
		addView(this.previewView);
		this.finderView = new FinderView(context);
		addView(this.finderView);
		
		this.multiFormatReader = new MultiFormatReader();
		Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
		Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
		decodeFormats.addAll(DecodeFormat.PRODUCT_FORMATS);
		decodeFormats.addAll(DecodeFormat.INDUSTRIAL_FORMATS);
		decodeFormats.addAll(DecodeFormat.QR_CODE_FORMATS);
		decodeFormats.addAll(DecodeFormat.DATA_MATRIX_FORMATS);
		decodeFormats.addAll(DecodeFormat.AZTEC_FORMATS);
		decodeFormats.addAll(DecodeFormat.PDF417_FORMATS);
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
		//if (characterSet != null) {
		//	hints.put(DecodeHintType.CHARACTER_SET, characterSet);
		//}
		this.multiFormatReader.setHints(hints);
	}
	
	public void setOnScanListener(OnScanListener listener) {
		this.onScanListener = listener;
	}
	
	public void startScan() {
		if (this.cameraManager == null || !this.cameraManager.isOpened()) {
			this.cameraManager = new CameraManager(getContext());
			this.finderView.setCameraManager(this.cameraManager);
		}
		
		SurfaceHolder surfaceHolder = this.previewView.getHolder();
		if (this.isSurfaced) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
		}
	}
	
	public void stopScan() {
		if (this.captureHandler != null) {
			this.captureHandler.quitSynchronously();
			this.captureHandler = null;
		}
		
		this.cameraManager.closeDriver();
		if (this.isSurfaced) {
			this.previewView.getHolder().removeCallback(this);
		}
	}
	
	private void initCamera(SurfaceHolder surfaceHolder) {
		if (this.cameraManager.isOpened()) {
			return;
		}
		
		try {
			this.cameraManager.openDriver(surfaceHolder);
			if (!this.cameraManager.isOpened()) {
				return;
			}
			initPreview();
			
			captureHandler = new CaptureHandler(this, this.cameraManager);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void initPreview() {
		int measuredWidth = this.previewView.getMeasuredWidth();
		int measuredHeight = this.previewView.getMeasuredHeight();
		if (measuredWidth == 0 || measuredHeight == 0) {
			return;
		}
		
		Point previewResolution = this.cameraManager.getCameraConfigManager().previewResolution;
		if (measuredWidth != previewResolution.x || measuredHeight != previewResolution.y) {
			requestLayout();
		}
	}
	
	public MultiFormatReader getMultiFormatReader() {
		return this.multiFormatReader;
	}
	
	Rect getPreviewRect() {
		return this.cameraManager.getPreviewRect(this, this.previewView);
	}
	
	void restartFinder() {
		this.finderView.drawFinderView();
	}
	
	void decodeSuccess(String result, Bitmap barcodeBitmap, float scaleFactor) {
		this.finderView.drawBarcodeView(barcodeBitmap);
		
		if (this.onScanListener != null) {
			this.onScanListener.scanResult(result);
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		Point previewResolution = this.cameraManager.getCameraConfigManager().previewResolution;
		if (previewResolution == null) {
			return;
		}
		
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		
		int previewWidth = previewResolution.x;
		int previewHeight = previewResolution.y;
		
		double ratio = (double) previewWidth / (double) previewHeight;
		
		int widthOffset = previewWidth - widthSize;
		int heightOffset = previewHeight - heightSize;
		
		int absWidthOffset = Math.abs(widthOffset);
		int absHeightOffset = Math.abs(heightOffset);
		
		int measuredWidth;
		int measuredHeight;
		
		if (widthOffset < 0 && heightOffset < 0) {
			// 预览分辨率宽高比控件分辨率宽高小
			if (absWidthOffset > absHeightOffset) {
				measuredWidth = widthSize;
				measuredHeight = (int) (widthSize / ratio);
			} else {
				measuredWidth = (int) (heightSize * ratio);
				measuredHeight = heightSize;
			}
		} else if (widthOffset < 0 && heightOffset > 0) {
			// 预览分辨率宽度比控件分辨率宽度大，预览分辨率高度比控件分辨率高度小
			measuredWidth = widthSize;
			measuredHeight = (int) (widthSize / ratio);
		} else if (widthOffset > 0 && heightOffset < 0) {
			// 预览分辨率宽度比控件分辨率宽度小，预览分辨率高度比控件分辨率高度大
			measuredWidth = (int) (heightSize * ratio);
			measuredHeight = heightSize;
		} else {
			measuredWidth = previewWidth;
			measuredHeight = previewHeight;
		}
		
		this.previewView.measure(
				MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
		);
	}
	
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		
		int width = right - left;
		int height = bottom - top;
		
		int measuredWidth = this.previewView.getMeasuredWidth();
		int measureHeight = this.previewView.getMeasuredHeight();
		int widthOffset = (width - measuredWidth) / 2;
		int heightOffset = (height - measureHeight) / 2;
		this.previewView.layout(
				widthOffset,
				heightOffset,
				widthOffset + measuredWidth,
				heightOffset + measureHeight
		);
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
		if (!this.isSurfaced) {
			this.isSurfaced = true;
			initCamera(surfaceHolder);
		}
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
		this.isSurfaced = false;
	}
	
	public interface OnScanListener {
		void scanResult(String result);
	}
}
