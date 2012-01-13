package com.ericharlow.DragNDrop;

import com.androzic.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;

public class DragNDropListView extends ListView
{

	private boolean mDragMode;

	private int mStartPosition;
	private int mEndPosition;
	private int mDragPointOffset; // Used to adjust drag view location
	private int mDragItemY;

	ImageView mDragView;
	GestureDetector mGestureDetector;

	DropListener mDropListener;
	RemoveListener mRemoveListener;
	DragListener mDragListener;

	public DragNDropListView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void setDropListener(DropListener l)
	{
		mDropListener = l;
	}

	public void setRemoveListener(RemoveListener l)
	{
		mRemoveListener = l;
	}

	public void setDragListener(DragListener l)
	{
		mDragListener = l;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		final int action = ev.getAction();
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();

		if (action == MotionEvent.ACTION_DOWN && x < 40)
		{
			mDragMode = true;
		}

		if (!mDragMode)
			return super.onTouchEvent(ev);

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
				mStartPosition = pointToPosition(x, y);
				if (mStartPosition != INVALID_POSITION)
				{
					int mItemPosition = mStartPosition - getFirstVisiblePosition();
					mDragItemY = getChildAt(mItemPosition).getTop();
					mDragPointOffset = y - mDragItemY;
					mDragPointOffset -= ((int) ev.getRawY()) - y;
					startDrag(mItemPosition, y);
					drag(x, y);
				}
				break;
			case MotionEvent.ACTION_MOVE:
				drag(x, y);
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
			default:
				mDragMode = false;
				mEndPosition = pointToPosition(x, y);
				stopDrag(mStartPosition - getFirstVisiblePosition(), x, y);
				if (mDropListener != null && mStartPosition != INVALID_POSITION && mEndPosition != INVALID_POSITION)
					mDropListener.onDrop(mStartPosition, mEndPosition);
				break;
		}
		return true;
	}

	// move the drag view
	private void drag(int x, int y)
	{
		WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) mDragView.getLayoutParams();
		layoutParams.x = mRemoveListener != null ? x : 0;
		layoutParams.y = y - mDragPointOffset;
		WindowManager mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.updateViewLayout(mDragView, layoutParams);

		if (mRemoveListener != null && x > mDragView.getWidth() * 0.7 && y - mDragItemY > 0 && y - mDragItemY < mDragView.getHeight())
		{
			mDragView.setBackgroundResource(R.drawable.alert);
		}
		else if (mRemoveListener != null)
		{
			mDragView.setBackgroundResource(android.R.drawable.alert_dark_frame);
		}

		if (mDragListener != null)
			mDragListener.onDrag(x, y, null);// change null to "this" when ready to use
	}

	// enable the drag view for dragging
	private void startDrag(int itemIndex, int y)
	{
		stopDrag(itemIndex, 0, y);

		View item = getChildAt(itemIndex);
		if (item == null)
			return;
		item.setDrawingCacheEnabled(true);
		if (mDragListener != null)
			mDragListener.onStartDrag(item);

		// Create a copy of the drawing cache so that it does not get recycled
		// by the framework when the list tries to clean up memory
		Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());

		WindowManager.LayoutParams mWindowParams = new WindowManager.LayoutParams();
		mWindowParams.gravity = Gravity.TOP;
		mWindowParams.x = 0;
		mWindowParams.y = y - mDragPointOffset;

		mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		mWindowParams.format = PixelFormat.TRANSLUCENT;
		mWindowParams.windowAnimations = 0;

		Context context = getContext();
		ImageView v = new ImageView(context);
		v.setImageBitmap(bitmap);

		WindowManager mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(v, mWindowParams);
		mDragView = v;
	}

	// destroy drag view
	private void stopDrag(int itemIndex, int x, int y)
	{
		if (mDragView != null)
		{
			View item = getChildAt(itemIndex);
			if (mDragListener != null)
				mDragListener.onStopDrag(item);
			if (mRemoveListener != null && x > mDragView.getWidth() * 0.7 && y - mDragItemY > 0 && y - mDragItemY < mDragView.getHeight())
			{
				mRemoveListener.onRemove(itemIndex);
			}
			mDragView.setVisibility(GONE);
			WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
			wm.removeView(mDragView);
			mDragView.setImageDrawable(null);
			mDragView = null;
		}
	}
}
