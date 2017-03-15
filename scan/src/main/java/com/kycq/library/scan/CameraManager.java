package com.kycq.library.scan;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.kycq.library.scan.open.OpenCamera;

import java.io.IOException;

class CameraManager implements Camera.PreviewCallback {
	private Context context;
	private OpenCamera openCamera;
	
	private CameraConfigManager cameraConfigManager;
	private AutoFocusManager autoFocusManager;
	
	private boolean isInitialized;
	private boolean isPreviewing;
	
	private Rect frameRect;
	private Rect previewFrameRect;
	// private boolean isFrameSquare = true;
	// private float frameWidthRatio = 0.6F;
	// private float frameHeightRatio = 0.6F;
	
	private Handler decodeHandler;
	private int decodeMessage;
	
	CameraManager(Context context) {
		this.context = context;
		this.cameraConfigManager = new CameraConfigManager();
	}
	
	CameraConfigManager getCameraConfigManager() {
		return this.cameraConfigManager;
	}
	
	boolean isOpened() {
		return this.openCamera != null;
	}
	
	void openDriver(SurfaceHolder surfaceHolder) throws IOException {
		OpenCamera theOpenCamera = this.openCamera;
		if (theOpenCamera == null) {
			try {
				theOpenCamera = OpenCamera.open();
			} catch (Exception ignored) {
				ignored.printStackTrace();
			}
			if (theOpenCamera == null) {
				return;
			}
			this.openCamera = theOpenCamera;
		}
		
		if (!this.isInitialized) {
			this.isInitialized = true;
			this.cameraConfigManager.initFromCameraParameters(context, theOpenCamera);
		}
		
		Camera theCamera = theOpenCamera.getCamera();
		Camera.Parameters cameraParameters = theCamera.getParameters();
		String parametersFlattened = cameraParameters == null ? null : cameraParameters.flatten();
		try {
			this.cameraConfigManager.setDesiredCameraParameters(theOpenCamera, false);
		} catch (RuntimeException re) {
			if (parametersFlattened != null) {
				cameraParameters = theCamera.getParameters();
				cameraParameters.unflatten(parametersFlattened);
				try {
					theCamera.setParameters(cameraParameters);
					this.cameraConfigManager.setDesiredCameraParameters(theOpenCamera, true);
				} catch (RuntimeException ignored) {
				}
			}
		}
		theCamera.setPreviewDisplay(surfaceHolder);
	}
	
	synchronized void startPreview() {
		OpenCamera theOpenCamera = this.openCamera;
		if (theOpenCamera != null && !this.isPreviewing) {
			theOpenCamera.getCamera().startPreview();
			this.isPreviewing = true;
			this.autoFocusManager = new AutoFocusManager(theOpenCamera.getCamera(), true);
		}
	}
	
	synchronized void stopPreview() {
		if (this.autoFocusManager != null) {
			this.autoFocusManager.stop();
			this.autoFocusManager = null;
		}
		if (this.openCamera != null && this.isPreviewing) {
			this.openCamera.getCamera().stopPreview();
			this.decodeHandler = null;
			this.decodeMessage = 0;
			this.isPreviewing = false;
		}
	}
	
	synchronized void closeDriver() {
		if (this.openCamera != null) {
			this.openCamera.getCamera().release();
			this.openCamera = null;
		}
	}
	
	// @SuppressWarnings("SuspiciousNameCombination")
	// Rect getFrameRect(ScanView scanView) {
	// 	if (this.frameRect != null) {
	// 		return this.frameRect;
	// 	}
	//
	// 	int measuredWidth = scanView.getMeasuredWidth();
	// 	int measuredHeight = scanView.getMeasuredHeight();
	// 	if (measuredWidth == 0 || measuredHeight == 0) {
	// 		return null;
	// 	}
	//
	// 	int width = (int) (measuredWidth * this.frameWidthRatio);
	// 	int height = (int) (measuredHeight * this.frameHeightRatio);
	//
	// 	if (this.isFrameSquare) {
	// 		if (width > height) {
	// 			width = height;
	// 		} else {
	// 			height = width;
	// 		}
	// 	}
	//
	// 	int offsetWidth = (measuredWidth - width) / 2;
	// 	int offsetHeight = (measuredHeight - height) / 2;
	// 	this.frameRect = new Rect(offsetWidth, offsetHeight, offsetWidth + width, offsetHeight + height);
	//
	// 	return this.frameRect;
	// }
	
	@SuppressWarnings("SuspiciousNameCombination")
	Rect getFrameRect() {
		// if (this.frameRect != null) {
		// 	return this.frameRect;
		// }
		//
		// int measuredWidth = scanView.getMeasuredWidth();
		// int measuredHeight = scanView.getMeasuredHeight();
		// if (measuredWidth == 0 || measuredHeight == 0) {
		// 	return null;
		// }
		//
		// int width = (int) (measuredWidth * this.frameWidthRatio);
		// int height = (int) (measuredHeight * this.frameHeightRatio);
		//
		// if (this.isFrameSquare) {
		// 	if (width > height) {
		// 		width = height;
		// 	} else {
		// 		height = width;
		// 	}
		// }
		//
		// int offsetWidth = (measuredWidth - width) / 2;
		// int offsetHeight = (measuredHeight - height) / 2;
		// this.frameRect = new Rect(offsetWidth, offsetHeight, offsetWidth + width, offsetHeight + height);
		
		return this.frameRect;
	}
	
	void setFrameRect(Rect frameRect) {
		this.frameRect = frameRect;
	}
	
	Rect getPreviewRect(ScanView scanView, SurfaceView previewView) {
		if (this.previewFrameRect != null) {
			return this.previewFrameRect;
		}
		
		this.previewFrameRect = new Rect();
		int widthOffset = (previewView.getMeasuredWidth() - scanView.getMeasuredWidth()) / 2;
		int heightOffset = (previewView.getMeasuredHeight() - scanView.getMeasuredHeight()) / 2;
		
		this.previewFrameRect.set(
				this.frameRect.left + widthOffset,
				this.frameRect.top + heightOffset,
				this.frameRect.right + widthOffset,
				this.frameRect.bottom + heightOffset
		);
		return this.previewFrameRect;
	}
	
	synchronized void requestPreviewFrame(DecodeHandler decodeHandler, int decodeMessage) {
		OpenCamera theOpenCamera = this.openCamera;
		if (theOpenCamera != null && isPreviewing) {
			this.decodeHandler = decodeHandler;
			this.decodeMessage = decodeMessage;
			theOpenCamera.getCamera().setOneShotPreviewCallback(this);
		}
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Point cameraResolution = this.cameraConfigManager.cameraResolution;
		Handler theDecodeHandler = this.decodeHandler;
		if (cameraResolution != null && theDecodeHandler != null) {
			DecodeHandler.DecodeInfo decodeInfo = new DecodeHandler.DecodeInfo();
			decodeInfo.rotationAngle = this.cameraConfigManager.cameraRotation;
			decodeInfo.decodeWidth = cameraResolution.x;
			decodeInfo.decodeHeight = cameraResolution.y;
			decodeInfo.decodeData = data;
			Message message = theDecodeHandler.obtainMessage(this.decodeMessage, decodeInfo);
			message.sendToTarget();
			this.decodeHandler = null;
		}
	}
}
