package com.androzic.ui;

/*
 * Andrey Novikov, 2010.
 */

import android.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

public class ColorPickerDialog extends AlertDialog implements OnClickListener, OnColorChangedListener
{
    private static final String COLOR = "color";

	private OnColorChangedListener mListener;
	private ColorPickerView mColorPicker;
    
	public ColorPickerDialog(Context context, OnColorChangedListener listener, int initialColor, int defaultColor, boolean buttons)
	{
		super(context);

        mListener = listener;
        mColorPicker = new ColorPickerView(getContext(), this, initialColor);
        setView(mColorPicker);
        
        if (buttons)
        {
	        setButton(AlertDialog.BUTTON_POSITIVE, context.getText(R.string.ok), this);
	        setButton(AlertDialog.BUTTON_NEGATIVE, context.getText(R.string.cancel), (OnClickListener) null);
        }
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (mListener != null)
		{
			mListener.colorChanged(mColorPicker.getColor());
		}
	}

	@Override
	public void colorChanged(int color)
	{
		onClick(this, AlertDialog.BUTTON_POSITIVE);
		dismiss();
	}

    @Override
    public Bundle onSaveInstanceState()
    {
        Bundle state = super.onSaveInstanceState();
        state.putInt(COLOR, mColorPicker.getColor());
        return state;
    }
    
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        int color = savedInstanceState.getInt(COLOR);
        mColorPicker.setColor(color);
        mColorPicker.setOnColorChangedListener(this);
    }

}
