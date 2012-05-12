package com.androzic.ui;

import net.londatiga.android.PopupWindows;
import android.content.Context;
import android.graphics.Rect;
import android.text.Html;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;

import com.androzic.R;

public class QuickView extends PopupWindows implements OnDismissListener
{
	private ImageView mArrowUp;
	private ImageView mArrowDown;
	private LayoutInflater inflater;
	private TextView mText;
	private OnDismissListener mDismissListener;

	private int arrowWidth=0;

	private int mAnimStyle;

	public static final int ANIM_GROW_FROM_LEFT = 1;
	public static final int ANIM_GROW_FROM_RIGHT = 2;
	public static final int ANIM_GROW_FROM_CENTER = 3;
	public static final int ANIM_AUTO = 4;

	/**
	 * Constructor.
	 * 
	 * @param context
	 *            Context
	 */
	public QuickView(Context context)
	{
		super(context);

		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		setRootViewId(R.layout.quickview);

		mAnimStyle = ANIM_AUTO;
	}

	public void setText(CharSequence text)
	{
		if (mText != null)
		{
			mText.setText(Html.fromHtml(text.toString().replace("\n", "<br/>")));
			Linkify.addLinks(mText, Linkify.ALL);
		}
	}

	/**
	 * Set root view.
	 * 
	 * @param id
	 *            Layout resource id
	 */
	public void setRootViewId(int id)
	{
		mRootView = (ViewGroup) inflater.inflate(id, null);
		mText = (TextView) mRootView.findViewById(R.id.text);
		mArrowDown = (ImageView) mRootView.findViewById(R.id.arrow_down);
		mArrowUp = (ImageView) mRootView.findViewById(R.id.arrow_up);
		mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		setContentView(mRootView);
	}

	/**
	 * Set animation style.
	 * 
	 * @param mAnimStyle
	 *            animation style, default is set to ANIM_AUTO
	 */
	public void setAnimStyle(int mAnimStyle)
	{
		this.mAnimStyle = mAnimStyle;
	}

	/**
	 * Show popup mWindow
	 */
	public void show(View anchor)
	{
		preShow();

		int[] location = new int[2];

		anchor.getLocationOnScreen(location);

		Rect anchorRect = new Rect(location[0] + anchor.getPaddingLeft(), location[1] + anchor.getPaddingTop(), location[0] + anchor.getWidth() - anchor.getPaddingRight(), location[1] + anchor.getHeight() - anchor.getPaddingBottom());

		int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
		int screenHeight = mWindowManager.getDefaultDisplay().getHeight();

		mRootView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//		if (mRootView.getMeasuredWidth() > screenWidth)
//		{
//			mRootView.setLayoutParams(new LayoutParams(screenWidth / 2, LayoutParams.WRAP_CONTENT));
//			mRootView.measure(screenWidth / 2, LayoutParams.WRAP_CONTENT);
//		}

		if (arrowWidth == 0)
		{
			arrowWidth = mArrowUp.getMeasuredWidth();
		}
		
		int rootWidth = screenWidth > screenHeight ? screenWidth / 2 : screenWidth * 2 / 3;
		int rootHeight = mRootView.getMeasuredHeight();
		int x = anchorRect.width() / 2;
		
		int xPos = anchorRect.left + x - rootWidth / 2;
		int yPos = 0;
		int arrowPos = rootWidth / 2;
		if (xPos < 0) {
			xPos = 0;
			arrowPos = x;
		}
		if ((xPos + rootWidth) > screenWidth) {
			xPos = screenWidth - rootWidth;
			arrowPos = anchorRect.left + x - xPos;
		}
		if ((arrowPos - arrowWidth / 2) < 0)
			arrowPos = arrowWidth / 2;
		if ((arrowPos + xPos + arrowWidth / 2) > screenWidth)
			arrowPos = rootWidth - arrowWidth / 2;
		
		int dyTop			= anchorRect.top + anchorRect.height();
//		int dyBottom		= screenHeight - anchorRect.bottom;

		boolean onTop		= ((anchorRect.height() + rootHeight) > screenHeight) ? true : false;

		if (onTop) {
			yPos = dyTop - rootHeight;
//			if (rootHeight > dyTop) {
//				LayoutParams l 	= mScroller.getLayoutParams();
//				l.height		= dyTop - anchorRect.top;
//			}
		} else {
			yPos = dyTop;
//			if (rootHeight > dyBottom) {
//				LayoutParams l 	= mScroller.getLayoutParams();
//				l.height		= dyBottom;
//			}
		}
		
		showArrow(((onTop) ? R.id.arrow_down : R.id.arrow_up), arrowPos);
		
		setAnimationStyle(screenWidth, anchorRect.centerX(), onTop);
		
		mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
		mWindow.update(rootWidth, LayoutParams.WRAP_CONTENT);
	}

	/**
	 * Set animation style
	 * 
	 * @param screenWidth
	 *            Screen width
	 * @param requestedX
	 *            distance from left screen
	 * @param onTop
	 *            flag to indicate where the popup should be displayed. Set TRUE
	 *            if displayed on top of anchor and vice versa
	 */
	private void setAnimationStyle(int screenWidth, int requestedX, boolean onTop)
	{
		int arrowPos = requestedX - mArrowUp.getMeasuredWidth() / 2;

		switch (mAnimStyle)
		{
			case ANIM_GROW_FROM_LEFT:
				mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
				break;

			case ANIM_GROW_FROM_RIGHT:
				mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
				break;

			case ANIM_GROW_FROM_CENTER:
				mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
				break;

			case ANIM_AUTO:
				if (arrowPos <= screenWidth / 4)
				{
					mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
				}
				else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4))
				{
					mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
				}
				else
				{
					mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopDownMenu_Right : R.style.Animations_PopDownMenu_Right);
				}

				break;
		}
	}

	/**
	 * Show arrow
	 * 
	 * @param whichArrow
	 *            arrow type resource id
	 * @param requestedX
	 *            distance from left screen
	 */
	private void showArrow(int whichArrow, int requestedX)
	{
		final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
		final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

		final int arrowWidth = mArrowUp.getMeasuredWidth();

		showArrow.setVisibility(View.VISIBLE);

		ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) showArrow.getLayoutParams();

		param.leftMargin = requestedX - arrowWidth / 2;

		hideArrow.setVisibility(View.INVISIBLE);
	}

	/**
	 * Set listener for window dismissed. This listener will only be fired if
	 * the quicakction dialog is dismissed by clicking outside the dialog or
	 * clicking on sticky item.
	 */
	public void setOnDismissListener(QuickView.OnDismissListener listener)
	{
		setOnDismissListener(this);

		mDismissListener = listener;
	}

	@Override
	public void onDismiss()
	{
		if (mDismissListener != null)
		{
			mDismissListener.onDismiss();
		}
	}

	/**
	 * Listener for window dismiss
	 * 
	 */
	public interface OnDismissListener
	{
		public abstract void onDismiss();
	}
}
