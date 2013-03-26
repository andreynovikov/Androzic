package com.androzic.track;

import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.androzic.R;
import com.androzic.data.Track;
import com.googlecode.android.widgets.DateSlider.SliderContainer;

@SuppressLint("ValidFragment")
public class TrackExportDialog extends SherlockDialogFragment implements TextWatcher
{
	private EditText nameText;
	private Spinner formatSpinner;
	private SliderContainer fromSliderContainer;
	private SliderContainer tillSliderContainer;
	private Button saveButton;

	private Track track;
	
	private boolean validName;
	private boolean validDates;
	
	public TrackExportDialog()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

	public TrackExportDialog(Track track)
	{
		this.track = track;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.dlg_exporttrack, container);

		nameText = (EditText) view.findViewById(R.id.name_text);
		nameText.setFilters(new InputFilter[]{filter});
		nameText.addTextChangedListener(this);
		formatSpinner = (Spinner) view.findViewById(R.id.format_spinner);

		Track.TrackPoint start = track.getPoint(0);
		Calendar startTime = Calendar.getInstance();
		startTime.setTimeInMillis(start.time);
		Track.TrackPoint end = track.getLastPoint();
		Calendar endTime = Calendar.getInstance();
		endTime.setTimeInMillis(end.time);

		Calendar fromTime = Calendar.getInstance();
		fromTime.setTimeInMillis(end.time);
		Calendar tillTime = Calendar.getInstance();
		tillTime.setTimeInMillis(end.time);
		
		fromSliderContainer = (SliderContainer) view.findViewById(R.id.fromSliderContainer);
		fromSliderContainer.setMinuteInterval(1);
		fromSliderContainer.setTime(fromTime);
		fromSliderContainer.setMinTime(startTime);
		fromSliderContainer.setMaxTime(endTime);
		fromSliderContainer.setMinuteInterval(60);
		fromSliderContainer.setOnTimeChangeListener(onFromTimeChangeListener);
		tillSliderContainer = (SliderContainer) view.findViewById(R.id.tillSliderContainer);
		tillSliderContainer.setMinuteInterval(1);
		tillSliderContainer.setTime(tillTime);
		tillSliderContainer.setMinTime(startTime);
		tillSliderContainer.setMaxTime(endTime);
		tillSliderContainer.setMinuteInterval(60);
		tillSliderContainer.setOnTimeChangeListener(onTillTimeChangeListener);

		final Dialog dialog = getDialog();

		Button cancelButton = (Button) view.findViewById(R.id.cancel_button);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				dialog.cancel();
			}
		});
		saveButton = (Button) view.findViewById(R.id.save_button);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				dialog.cancel();
			}
		});
		
		validName = false;
		validDates = fromTime.compareTo(tillTime) <= 0;
		updateSaveButton();
		
		dialog.setTitle(R.string.exporttrack_name);
		dialog.setCanceledOnTouchOutside(false);
		return view;
	}

	private void updateSaveButton()
	{
		saveButton.setEnabled(validName && validDates);
	}

	private SliderContainer.OnTimeChangeListener onFromTimeChangeListener = new SliderContainer.OnTimeChangeListener() {

		public void onTimeChange(Calendar time)
		{
			validDates = time.compareTo(tillSliderContainer.getTime()) <= 0;
			updateSaveButton();
		}
	};

	private SliderContainer.OnTimeChangeListener onTillTimeChangeListener = new SliderContainer.OnTimeChangeListener() {

		public void onTimeChange(Calendar time)
		{
			validDates = time.compareTo(fromSliderContainer.getTime()) >= 0;
			updateSaveButton();
		}
	};

	@Override
	public void afterTextChanged(Editable s)
	{
		validName = s.length() > 0 && !"".equals(s.toString().trim());
		updateSaveButton();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
	}

	InputFilter filter = new InputFilter() {
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend)
		{
			for (int i = start; i < end; i++)
			{
                String resultingTxt = source.subSequence(start, end).toString();
                if (resultingTxt.matches(".*[\\/\\\\:;|].*"))
				{
					return "";
				}
			}
			return null;
		}
	};
}
