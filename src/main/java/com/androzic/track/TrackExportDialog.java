package com.androzic.track;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.location.ILocationService;
import com.androzic.ui.ColorButton;
import com.androzic.util.FileUtils;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.googlecode.android.widgets.DateSlider.SliderContainer;

public class TrackExportDialog extends DialogFragment implements TextWatcher
{
	private EditText nameText;
	private Spinner formatSpinner;
	private ColorButton color;
	private SliderContainer fromSliderContainer;
	private SliderContainer tillSliderContainer;
	private Button saveButton;

	private boolean validName;
	private boolean validDates;
	private ILocationService locationService;

	public TrackExportDialog()
	{
		Androzic application = Androzic.getApplication();
		locationService = application.getLocationService();
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		View view = inflater.inflate(R.layout.dlg_exporttrack, container);

		nameText = (EditText) view.findViewById(R.id.name_text);
		nameText.setFilters(new InputFilter[] { filter });
		nameText.addTextChangedListener(this);
		formatSpinner = (Spinner) view.findViewById(R.id.format_spinner);

		color = (ColorButton) view.findViewById(R.id.color_button);
		color.setColor(prefs.getInt(getString(R.string.pref_tracking_currentcolor), getResources().getColor(R.color.currenttrack)), Color.RED);

		Calendar startTime = Calendar.getInstance();
		startTime.setTimeInMillis(locationService.getTrackStartTime());
		Calendar endTime = Calendar.getInstance();
		endTime.setTimeInMillis(locationService.getTrackEndTime());

		fromSliderContainer = (SliderContainer) view.findViewById(R.id.fromSliderContainer);
		fromSliderContainer.setMinuteInterval(1);
		fromSliderContainer.setTime(endTime);
		fromSliderContainer.setMinTime(startTime);
		fromSliderContainer.setMaxTime(endTime);
		fromSliderContainer.setMinuteInterval(60);
		fromSliderContainer.setOnTimeChangeListener(onFromTimeChangeListener);
		tillSliderContainer = (SliderContainer) view.findViewById(R.id.tillSliderContainer);
		tillSliderContainer.setMinuteInterval(1);
		tillSliderContainer.setTime(endTime);
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
		saveButton.setOnClickListener(saveOnClickListener);

		validName = false;
		validDates = true;
		updateSaveButton();

		dialog.setTitle(R.string.exporttrack_name);
		dialog.setCanceledOnTouchOutside(false);
		return view;
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
		getDialog().getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private OnClickListener saveOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			final Activity activity = getActivity();
			final Androzic application = Androzic.getApplication();

			final ProgressDialog pd = new ProgressDialog(activity);
			pd.setIndeterminate(true);
			pd.setMessage(getString(R.string.msg_wait));
			pd.setCancelable(false);
			pd.show();

			new Thread(new Runnable() {
				public void run()
				{
					String name = nameText.getText().toString();
					String format = formatSpinner.getItemAtPosition(formatSpinner.getSelectedItemPosition()).toString();
					String filename = FileUtils.sanitizeFilename(name) + format;

					Calendar startTime = fromSliderContainer.getTime();
					startTime.set(Calendar.HOUR_OF_DAY, 0);
					startTime.set(Calendar.MINUTE, 0);
					startTime.set(Calendar.SECOND, 0);
					startTime.set(Calendar.MILLISECOND, 0);
					long start = startTime.getTimeInMillis();
					Calendar endTime = tillSliderContainer.getTime();
					endTime.set(Calendar.HOUR_OF_DAY, 23);
					endTime.set(Calendar.MINUTE, 59);
					endTime.set(Calendar.SECOND, 59);
					endTime.set(Calendar.MILLISECOND, 999);
					long end = endTime.getTimeInMillis();
					
					Track track = locationService.getTrack(start, end);
					List<Track.TrackPoint> points = track.getAllPoints();
					
					if (points.size() < 2)
					{
						activity.runOnUiThread(new Runnable() {
							public void run()
							{
								Toast.makeText(activity, R.string.msg_emptytracksegment, Toast.LENGTH_LONG).show();
							}
						});
						pd.dismiss();
						return;
					}

					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
					track.name = name;
					track.width = prefs.getInt(getString(R.string.pref_tracking_linewidth), getResources().getInteger(R.integer.def_track_linewidth));
					track.color = color.getColor();

					try
					{
						File dir = new File(application.dataPath);
						if (!dir.exists())
							dir.mkdirs();
						File file = new File(dir, filename);
						if (!file.exists())
						{
							file.createNewFile();
						}
						if (file.canWrite())
						{
							if (".plt".equals(format))
							{
								OziExplorerFiles.saveTrackToFile(file, application.charset, track);
							}
							else if (".kml".equals(format))
							{
								KmlFiles.saveTrackToFile(file, track);
							}
							else if (".gpx".equals(format))
							{
								GpxFiles.saveTrackToFile(file, track);
							}
						}
						dismiss();
					}
					catch (Exception e)
					{
						Log.e("TrackExport", e.toString(), e);
						activity.runOnUiThread(new Runnable() {
							public void run()
							{
								Toast.makeText(activity, R.string.err_write, Toast.LENGTH_LONG).show();
							}
						});
					}
					pd.dismiss();
				}
			}).start();
		}
	};

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
