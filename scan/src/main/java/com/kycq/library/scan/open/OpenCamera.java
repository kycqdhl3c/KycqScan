package com.kycq.library.scan.open;

import android.hardware.Camera;

public final class OpenCamera {
	private static final int NO_REQUESTED_CAMERA = -1;
	
	private final int index;
	private final Camera camera;
	private final CameraFacing facing;
	private final int orientation;
	
	private OpenCamera(int index, Camera camera, CameraFacing facing, int orientation) {
		this.index = index;
		this.camera = camera;
		this.facing = facing;
		this.orientation = orientation;
	}
	
	public Camera getCamera() {
		return camera;
	}
	
	public CameraFacing getFacing() {
		return facing;
	}
	
	public int getOrientation() {
		return orientation;
	}
	
	@Override
	public String toString() {
		return "index: " + index + ", facing: " + facing + ", orientation: " + orientation;
	}
	
	public static OpenCamera open() {
		return open(NO_REQUESTED_CAMERA);
	}
	
	public static OpenCamera open(int cameraId) {
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras == 0) {
			return null;
		}
		
		boolean explicitRequest = cameraId >= 0;
		
		Camera.CameraInfo selectedCameraInfo = null;
		int index;
		if (explicitRequest) {
			index = cameraId;
			selectedCameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(index, selectedCameraInfo);
		} else {
			index = 0;
			while (index < numCameras) {
				Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(index, cameraInfo);
				CameraFacing reportedFacing = CameraFacing.values()[cameraInfo.facing];
				if (reportedFacing == CameraFacing.BACK) {
					selectedCameraInfo = cameraInfo;
					break;
				}
				index++;
			}
		}
		
		Camera camera;
		if (index < numCameras) {
			camera = Camera.open(index);
		} else {
			if (explicitRequest) {
				camera = null;
			} else {
				camera = Camera.open(0);
				selectedCameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(0, selectedCameraInfo);
			}
		}
		
		if (camera == null || selectedCameraInfo == null) {
			return null;
		}
		return new OpenCamera(index, camera, CameraFacing.values()[selectedCameraInfo.facing], selectedCameraInfo.orientation);
	}
}
