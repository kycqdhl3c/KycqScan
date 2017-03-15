package com.kycq.library.scan;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.kycq.library.scan.open.CameraFacing;
import com.kycq.library.scan.open.OpenCamera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

class CameraConfigManager {
	private static final int MIN_PREVIEW_PIXELS = 480 * 320;
	private static final double MAX_ASPECT_DISTORTION = 0.15;
	
	private static final float MAX_EXPOSURE_COMPENSATION = 1.5f;
	private static final float MIN_EXPOSURE_COMPENSATION = 0.0f;
	
	private static final int AREA_PER_1000 = 400;
	
	int cameraRotation;
	Point cameraResolution;
	Point previewResolution;
	
	void initFromCameraParameters(Context context, OpenCamera openCamera) {
		Camera.Parameters cameraParameters = openCamera.getCamera().getParameters();
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		
		int displayRotation = display.getRotation();
		int rotationFromNaturalToDisplay;
		switch (displayRotation) {
			case Surface.ROTATION_0:
				rotationFromNaturalToDisplay = 0;
				break;
			case Surface.ROTATION_90:
				rotationFromNaturalToDisplay = 90;
				break;
			case Surface.ROTATION_180:
				rotationFromNaturalToDisplay = 180;
				break;
			case Surface.ROTATION_270:
				rotationFromNaturalToDisplay = 270;
				break;
			default:
				if (displayRotation % 90 == 0) {
					rotationFromNaturalToDisplay = (360 + displayRotation) % 360;
				} else {
					throw new IllegalArgumentException("Bad rotation: " + displayRotation);
				}
		}
		
		int rotationFromNaturalToCamera = openCamera.getOrientation();
		
		if (openCamera.getFacing() == CameraFacing.FRONT) {
			rotationFromNaturalToCamera = (360 - rotationFromNaturalToCamera) % 360;
		}
		
		this.cameraRotation = (360 + rotationFromNaturalToCamera - rotationFromNaturalToDisplay) % 360;
		
		Point screenResolution = new Point();
		display.getSize(screenResolution);
		
		this.cameraResolution = findBestPreviewSizeValue(cameraParameters, screenResolution);
		
		boolean isScreenPortrait = screenResolution.x < screenResolution.y;
		boolean isPreviewSizePortrait = this.cameraResolution.x < this.cameraResolution.y;
		
		if (isScreenPortrait == isPreviewSizePortrait) {
			this.previewResolution = new Point(this.cameraResolution.x, this.cameraResolution.y);
		} else {
			// noinspection SuspiciousNameCombination
			this.previewResolution = new Point(this.cameraResolution.y, this.cameraResolution.x);
		}
	}
	
	void setDesiredCameraParameters(OpenCamera openCamera, boolean safeMode) {
		Camera theCamera = openCamera.getCamera();
		Camera.Parameters cameraParameters = theCamera.getParameters();
		if (cameraParameters == null) {
			return;
		}
		
		initializeTorch(cameraParameters, safeMode);
		
		// setFocus(cameraParameters, mPreviewView.isAutoFocus(), mPreviewView.isDisableContinuousFocus(), safeMode);
		
		if (!safeMode) {
			// if (mPreviewView.isInvertScan()) {
			// 	setInvertColor(cameraParameters);
			// }
			//
			// if (!mPreviewView.isDisableBarcodeSceneMode()) {
			// 	setBarcodeSceneMode(cameraParameters);
			// }
			//
			// if (!mPreviewView.isDisableMetering()) {
			// 	setVideoStabilization(cameraParameters);
			// 	setFocusArea(cameraParameters);
			// 	setMetering(cameraParameters);
			// }
		}
		
		cameraParameters.setPreviewSize(this.cameraResolution.x, this.cameraResolution.y);
		
		theCamera.setParameters(cameraParameters);
		// 当前相机角度旋转度数
		theCamera.setDisplayOrientation(this.cameraRotation);
		
		// 更新最终相机分辨率大小
		Camera.Parameters afterParameters = theCamera.getParameters();
		Camera.Size afterSize = afterParameters.getPreviewSize();
		if (afterSize != null &&
				(this.cameraResolution.x != afterSize.width || this.cameraResolution.y != afterSize.height)) {
			this.cameraResolution.x = afterSize.width;
			this.cameraResolution.y = afterSize.height;
		}
	}
	
	private static Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {
		List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
		if (rawSupportedSizes == null) {
			Camera.Size defaultSize = parameters.getPreviewSize();
			if (defaultSize == null) {
				throw new IllegalStateException("Parameters contained no preview size!");
			}
			return new Point(defaultSize.width, defaultSize.height);
		}
		
		List<Camera.Size> supportedPreviewSizes = new ArrayList<>(rawSupportedSizes);
		Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
			@Override
			public int compare(Camera.Size a, Camera.Size b) {
				int aPixels = a.height * a.width;
				int bPixels = b.height * b.width;
				// 高分辨率排前，低分辨率排后
				if (bPixels < aPixels) {
					return -1;
				}
				if (bPixels > aPixels) {
					return 1;
				}
				return 0;
			}
		});
		
		Iterator<Camera.Size> it = supportedPreviewSizes.iterator();
		while (it.hasNext()) {
			Camera.Size supportedPreviewSize = it.next();
			int realWidth = supportedPreviewSize.width;
			int realHeight = supportedPreviewSize.height;
			if (realWidth * realHeight < MIN_PREVIEW_PIXELS) {
				it.remove();
				continue;
			}
			
			boolean isCandidatePortrait = realWidth < realHeight;
			int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
			int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
			
			// 宽高比例一致
			if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
				return new Point(realWidth, realHeight);
			}
		}
		
		// 宽高比例无法一致，取最优
		if (!supportedPreviewSizes.isEmpty()) {
			Camera.Size largestPreview = supportedPreviewSizes.get(0);
			return new Point(largestPreview.width, largestPreview.height);
		}
		
		// 支持列表为空，取默认值
		Camera.Size defaultPreview = parameters.getPreviewSize();
		if (defaultPreview == null) {
			throw new IllegalStateException("Parameters contained no preview size!");
		}
		return new Point(defaultPreview.width, defaultPreview.height);
	}
	
	private void initializeTorch(Camera.Parameters parameters, boolean safeMode) {
		// TODO
		// boolean currentSetting = mPreviewView.getFrontLightMode() == FrontLightMode.ON;
		// doSetTorch(parameters, currentSetting, safeMode);
	}
	
	private void doSetTorch(Camera.Parameters parameters, boolean newSetting, boolean safeMode) {
		setTorch(parameters, newSetting);
		// TODO
		// if (!safeMode && !mPreviewView.isDisableExposure()) {
		// 	setBestExposure(parameters, newSetting);
		// }
	}
	
	private static void setBestExposure(Camera.Parameters parameters, boolean lightOn) {
		int minExposure = parameters.getMinExposureCompensation();
		int maxExposure = parameters.getMaxExposureCompensation();
		float step = parameters.getExposureCompensationStep();
		if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
			float targetCompensation = lightOn ? MIN_EXPOSURE_COMPENSATION : MAX_EXPOSURE_COMPENSATION;
			int compensationSteps = Math.round(targetCompensation / step);
			compensationSteps = Math.max(Math.min(compensationSteps, maxExposure), minExposure);
			if (parameters.getExposureCompensation() != compensationSteps) {
				parameters.setExposureCompensation(compensationSteps);
			}
		}
	}
	
	private static void setTorch(Camera.Parameters parameters, boolean on) {
		List<String> supportedFlashModes = parameters.getSupportedFlashModes();
		String flashMode;
		if (on) {
			flashMode = findSettableValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
		} else {
			flashMode = findSettableValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_OFF);
		}
		if (flashMode != null) {
			if (!flashMode.equals(parameters.getFlashMode())) {
				parameters.setFlashMode(flashMode);
			}
		}
	}
	
	private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
		if (supportedValues != null) {
			for (String desiredValue : desiredValues) {
				if (supportedValues.contains(desiredValue)) {
					return desiredValue;
				}
			}
		}
		return null;
	}
	
	private static void setFocus(Camera.Parameters parameters, boolean autoFocus, boolean disableContinuous, boolean safeMode) {
		List<String> supportedFocusModes = parameters.getSupportedFocusModes();
		String focusMode = null;
		if (autoFocus) {
			if (safeMode || disableContinuous) {
				focusMode = findSettableValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO);
			} else {
				focusMode = findSettableValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Camera.Parameters.FOCUS_MODE_AUTO);
			}
		}
		if (!safeMode && focusMode == null) {
			focusMode = findSettableValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_MACRO, Camera.Parameters.FOCUS_MODE_EDOF);
		}
		if (focusMode != null) {
			if (!focusMode.equals(parameters.getFocusMode())) {
				parameters.setFocusMode(focusMode);
			}
		}
	}
	
	private static void setInvertColor(Camera.Parameters parameters) {
		if (Camera.Parameters.EFFECT_NEGATIVE.equals(parameters.getColorEffect())) {
			return;
		}
		String colorMode = findSettableValue(parameters.getSupportedColorEffects(), Camera.Parameters.EFFECT_NEGATIVE);
		if (colorMode != null) {
			parameters.setColorEffect(colorMode);
		}
	}
	
	private static void setBarcodeSceneMode(Camera.Parameters parameters) {
		if (Camera.Parameters.SCENE_MODE_BARCODE.equals(parameters.getSceneMode())) {
			return;
		}
		String sceneMode = findSettableValue(parameters.getSupportedSceneModes(), Camera.Parameters.SCENE_MODE_BARCODE);
		if (sceneMode != null) {
			parameters.setSceneMode(sceneMode);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	private static void setVideoStabilization(Camera.Parameters parameters) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			return;
		}
		if (parameters.isVideoStabilizationSupported()) {
			if (!parameters.getVideoStabilization()) {
				parameters.setVideoStabilization(true);
			}
		}
	}
	
	private static void setFocusArea(Camera.Parameters parameters) {
		if (parameters.getMaxNumFocusAreas() > 0) {
			List<Camera.Area> middleArea = buildMiddleArea(AREA_PER_1000);
			parameters.setFocusAreas(middleArea);
		}
	}
	
	private static void setMetering(Camera.Parameters parameters) {
		if (parameters.getMaxNumMeteringAreas() > 0) {
			List<Camera.Area> middleArea = buildMiddleArea(AREA_PER_1000);
			parameters.setMeteringAreas(middleArea);
		}
	}
	
	private static List<Camera.Area> buildMiddleArea(int areaPer1000) {
		return Collections.singletonList(new Camera.Area(new Rect(-areaPer1000, -areaPer1000, areaPer1000, areaPer1000), 1));
	}
}
