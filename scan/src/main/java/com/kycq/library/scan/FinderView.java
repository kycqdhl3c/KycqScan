package com.kycq.library.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

public class FinderView extends View {
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final float ANIMATION_TIME = 1500f;
	
	private static final float ANIMATION_RATIO = 1f;
	private static final float ALPHA_RATIO = 0.2f;
	private static final int OPAQUE_ALPHA = 255;
	
	private CameraManager cameraManager;
	
	private final Paint paint;
	private int maskColor = 0x60000000;
	private int cornerColor = 0xFFFFFFFF;
	private float cornerLineSize = 30;
	private float cornerStrokeWidth = 5;
	
	private Bitmap barcodeBitmap;
	private int barcodeColor = 0xB0000000;
	
	private Drawable animationDrawable = new ColorDrawable(0xAAFFFFFF);
	private float animationRatio;
	private long animationTime;
	
	private boolean isFrameSquare = true;
	private float frameWidthRatio = 0.6F;
	private float frameHeightRatio = 0.6F;
	
	private boolean isScanning = true;
	
	public FinderView(Context context) {
		super(context);
		
		this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	}
	
	void setCameraManager(CameraManager cameraManager) {
		this.cameraManager = cameraManager;
	}
	
	public void drawFinderView() {
		Bitmap barcodeBitmap = this.barcodeBitmap;
		this.barcodeBitmap = null;
		if (barcodeBitmap != null) {
			barcodeBitmap.recycle();
		}
		invalidate();
	}
	
	public void drawBarcodeView(Bitmap barcode) {
		this.barcodeBitmap = barcode;
		invalidate();
	}
	
	@SuppressWarnings("SuspiciousNameCombination")
	Rect getFrameRect() {
		if (this.cameraManager == null) {
			return null;
		}
		Rect frameRect = this.cameraManager.getFrameRect();
		if (frameRect != null) {
			return frameRect;
		}
		
		int measuredWidth = getMeasuredWidth();
		int measuredHeight = getMeasuredHeight();
		if (measuredWidth == 0 || measuredHeight == 0) {
			return null;
		}
		
		int width = (int) (measuredWidth * this.frameWidthRatio);
		int height = (int) (measuredHeight * this.frameHeightRatio);
		
		if (this.isFrameSquare) {
			if (width > height) {
				width = height;
			} else {
				height = width;
			}
		}
		
		int offsetWidth = (measuredWidth - width) / 2;
		int offsetHeight = (measuredHeight - height) / 2;
		frameRect = new Rect(offsetWidth, offsetHeight, offsetWidth + width, offsetHeight + height);
		setFrameRect(frameRect);
		
		return frameRect;
	}
	
	protected final void setFrameRect(Rect frameRect) {
		this.cameraManager.setFrameRect(frameRect);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		if (!this.isScanning) {
			return;
		}
		
		Rect frameRect = getFrameRect();
		if (frameRect == null) {
			return;
		}
		
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		
		// 上下左右的阴影区域
		this.paint.setColor(this.barcodeBitmap != null ? this.barcodeColor : this.maskColor);
		canvas.drawRect(0, 0, width, frameRect.top, this.paint);
		canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom + 1, this.paint);
		canvas.drawRect(frameRect.right + 1, frameRect.top, width, frameRect.bottom + 1, this.paint);
		canvas.drawRect(0, frameRect.bottom + 1, width, height, this.paint);
		
		this.paint.setColor(this.cornerColor);
		// 左上角
		drawLeftTopCorner(canvas, frameRect);
		// 右上角
		drawRightTopCorner(canvas, frameRect);
		// 左下角
		drawLeftBottomCorner(canvas, frameRect);
		// 右下角
		drawRightBottomCorner(canvas, frameRect);
		
		if (this.barcodeBitmap != null) {
			this.animationRatio = 0f;
			this.animationTime = 0L;
			this.paint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(this.barcodeBitmap, null, frameRect, this.paint);
		} else {
			long currentTime = System.currentTimeMillis();
			if (this.animationTime == 0L) {
				this.animationTime = currentTime;
			}
			float multiple = (currentTime - this.animationTime) / ANIMATION_TIME;
			this.animationTime = currentTime;
			this.animationRatio = this.animationRatio + multiple;
			if (this.animationRatio > ANIMATION_RATIO + ALPHA_RATIO) {
				this.animationRatio = this.animationRatio - ANIMATION_RATIO - ALPHA_RATIO;
			}
			
			int offsetY;
			int alpha;
			if (this.animationRatio < ANIMATION_RATIO) {
				offsetY = (int) (frameRect.height() * this.animationRatio);
				alpha = OPAQUE_ALPHA;
			} else {
				offsetY = frameRect.height();
				alpha = OPAQUE_ALPHA - (int) ((this.animationRatio - ANIMATION_RATIO) * OPAQUE_ALPHA / ALPHA_RATIO);
			}
			
			canvas.save();
			canvas.clipRect(frameRect.left, frameRect.top, frameRect.right, frameRect.bottom);
			this.animationDrawable.setBounds(
					frameRect.left, frameRect.top - frameRect.height() + offsetY,
					frameRect.right, frameRect.top + offsetY
			);
			this.animationDrawable.setAlpha(alpha);
			this.animationDrawable.draw(canvas);
			canvas.restore();
			
			postInvalidate(frameRect.left, frameRect.top, frameRect.right, frameRect.bottom);
		}
	}
	
	/**
	 * 左上角
	 *
	 * @param canvas 画布工具
	 * @param rect   绘制区域
	 */
	private void drawLeftTopCorner(Canvas canvas, Rect rect) {
		// 点
		canvas.drawRect(
				rect.left - this.cornerStrokeWidth, rect.top - this.cornerStrokeWidth,
				rect.left, rect.top,
				this.paint);
		// 横线
		canvas.drawRect(
				rect.left, rect.top - this.cornerStrokeWidth,
				rect.left + this.cornerLineSize, rect.top,
				this.paint);
		// 竖线
		canvas.drawRect(
				rect.left - this.cornerStrokeWidth, rect.top,
				rect.left, rect.top + this.cornerLineSize,
				this.paint);
	}
	
	/**
	 * 右上角
	 *
	 * @param canvas 画布工具
	 * @param rect   绘制区域
	 */
	private void drawRightTopCorner(Canvas canvas, Rect rect) {
		// 点
		canvas.drawRect(
				rect.right, rect.top - this.cornerStrokeWidth,
				rect.right + this.cornerStrokeWidth, rect.top,
				this.paint);
		// 横线
		canvas.drawRect(
				rect.right - this.cornerLineSize, rect.top - this.cornerStrokeWidth,
				rect.right, rect.top,
				this.paint);
		// 竖线
		canvas.drawRect(
				rect.right, rect.top,
				rect.right + this.cornerStrokeWidth, rect.top + this.cornerLineSize,
				this.paint);
	}
	
	/**
	 * 左下角
	 *
	 * @param canvas 画布工具
	 * @param rect   绘制区域
	 */
	private void drawLeftBottomCorner(Canvas canvas, Rect rect) {
		// 点
		canvas.drawRect(
				rect.left - this.cornerStrokeWidth, rect.bottom,
				rect.left, rect.bottom + this.cornerStrokeWidth,
				this.paint);
		// 横线
		canvas.drawRect(
				rect.left, rect.bottom,
				rect.left + this.cornerLineSize, rect.bottom + this.cornerStrokeWidth,
				this.paint);
		// 竖线
		canvas.drawRect(
				rect.left - this.cornerStrokeWidth, rect.bottom - this.cornerLineSize,
				rect.left, rect.bottom,
				this.paint);
	}
	
	/**
	 * 右下角
	 *
	 * @param canvas 画布工具
	 * @param rect   绘制区域
	 */
	private void drawRightBottomCorner(Canvas canvas, Rect rect) {
		// 点
		canvas.drawRect(
				rect.right, rect.bottom,
				rect.right + this.cornerStrokeWidth, rect.bottom + this.cornerStrokeWidth,
				this.paint);
		// 横线
		canvas.drawRect(
				rect.right - this.cornerLineSize, rect.bottom,
				rect.right, rect.bottom + this.cornerStrokeWidth,
				this.paint);
		// 竖线
		canvas.drawRect(
				rect.right, rect.bottom - this.cornerLineSize,
				rect.right + this.cornerStrokeWidth, rect.bottom,
				this.paint);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		Bitmap barcodeBitmap = this.barcodeBitmap;
		this.barcodeBitmap = null;
		if (barcodeBitmap != null) {
			barcodeBitmap.recycle();
		}
	}
}
