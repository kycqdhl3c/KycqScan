package com.kycq.library.scan;

import android.hardware.Camera;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

class AutoFocusManager implements Camera.AutoFocusCallback {
	private static final long AUTO_FOCUS_INTERVAL_MS = 2000L;
	private static final Collection<String> FOCUS_MODES_CALLING_AF;
	
	static {
		FOCUS_MODES_CALLING_AF = new ArrayList<>(2);
		FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_AUTO);
		FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_MACRO);
	}
	
	private final Camera mCamera;
	
	private boolean isStopped;
	private boolean isFocusing;
	private final boolean isUseAutoFocus;
	
	private AsyncTask<?, ?, ?> outstandingTask;
	
	AutoFocusManager(Camera camera, boolean isAutoFocus) {
		mCamera = camera;
		String currentFocusMode = camera.getParameters().getFocusMode();
		isUseAutoFocus = isAutoFocus && FOCUS_MODES_CALLING_AF.contains(currentFocusMode);
		start();
	}
	
	private synchronized void start() {
		if (isUseAutoFocus) {
			outstandingTask = null;
			if (!isStopped && !isFocusing) {
				try {
					mCamera.autoFocus(this);
					isFocusing = true;
				} catch (RuntimeException ignored) {
					autoFocusAgainLater();
				}
			}
		}
	}
	
	synchronized void stop() {
		isStopped = true;
		if (isUseAutoFocus) {
			cancelOutstandingTask();
			try {
				mCamera.cancelAutoFocus();
			} catch (RuntimeException ignored) {
			}
		}
	}
	
	private synchronized void cancelOutstandingTask() {
		if (outstandingTask != null) {
			if (outstandingTask.getStatus() != AsyncTask.Status.FINISHED) {
				outstandingTask.cancel(true);
			}
			outstandingTask = null;
		}
	}
	
	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		isFocusing = false;
		autoFocusAgainLater();
	}
	
	private synchronized void autoFocusAgainLater() {
		if (!isStopped && outstandingTask == null) {
			AutoFocusTask newTask = new AutoFocusTask();
			try {
				newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				outstandingTask = newTask;
			} catch (RejectedExecutionException ignored) {
			}
		}
	}
	
	private final class AutoFocusTask extends AsyncTask<Object, Object, Object> {
		@Override
		protected Object doInBackground(Object... voids) {
			try {
				Thread.sleep(AUTO_FOCUS_INTERVAL_MS);
			} catch (InterruptedException ignored) {
			}
			start();
			return null;
		}
	}
}
