package com.androzic.track;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.ColorButton;
import com.androzic.util.FileUtils;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.googlecode.android.widgets.DateSlider.SliderContainer;

@SuppressLint("ValidFragment")
public class TrackExportDialog extends SherlockDialogFragment implements TextWatcher
{
	private EditText nameText;
	private Spinner formatSpinner;
	private CheckBox skip;
	private ColorButton color;
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

		skip = (CheckBox) view.findViewById(R.id.skip_check);
        color = (ColorButton) view.findViewById(R.id.color_button);
        color.setColor(prefs.getInt(getString(R.string.pref_tracking_currentcolor), getResources().getColor(R.color.currenttrack)), Color.RED);

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
		saveButton.setOnClickListener(saveOnClickListener);

		validName = false;
		validDates = fromTime.compareTo(tillTime) <= 0;
		updateSaveButton();

		dialog.setTitle(R.string.exporttrack_name);
		dialog.setCanceledOnTouchOutside(false);
		return view;
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
	        		boolean skipSingles = skip.isChecked();
	        		
					String name = nameText.getText().toString();
					String format = formatSpinner.getItemAtPosition(formatSpinner.getSelectedItemPosition()).toString();
					String filename = FileUtils.sanitizeFilename(name) + format;

					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
					track.name = name;
					track.width = prefs.getInt(getString(R.string.pref_tracking_linewidth), getResources().getColor(R.integer.def_track_linewidth));
	        		track.color = color.getColor();

					List<Track.TrackPoint> points = track.getPoints();

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

					int sp = 0;
					int ep = points.size();
					for (int i = 0; i < ep; i++)
					{
						Track.TrackPoint point = points.get(i);
						if (point.time < start)
						{
							sp = i;
							continue;
						}
						if (point.time > end)
						{
							ep = i;
						}
					}
					if (ep < points.size())
						track.cutAfter(ep - 1);
					if (sp > 0)
						track.cutBefore(sp + 1);

					if (skipSingles)
					{
						Track.TrackPoint pp = track.getLastPoint();
						for (int i = points.size() - 2; i > 0; i--)
						{
							Track.TrackPoint cp = points.get(i);
							if (!pp.continous && !cp.continous)
							{
								track.removePoint(i + 1);
							}
							pp = cp;
						}
					}

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
						activity.runOnUiThread(new Runnable() 
						{
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
