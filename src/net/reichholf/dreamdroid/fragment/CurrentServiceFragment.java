/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.CurrentServiceRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Shows some information about the service currently running on TV
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceFragment extends AbstractHttpFragment implements ActionDialog.DialogActionListener {
	private ExtendedHashMap mCurrent;

	private TextView mServiceName;
	private TextView mProvider;
	private TextView mNowStart;
	private TextView mNowTitle;
	private TextView mNowDuration;
	private TextView mNextStart;
	private TextView mNextTitle;
	private TextView mNextDuration;
	private Button mStream;
	private LinearLayout mNowLayout;
	private LinearLayout mNextLayout;
	protected ProgressDialog mProgress;

	private ExtendedHashMap mService;
	private ExtendedHashMap mNow;
	private ExtendedHashMap mNext;
	private ExtendedHashMap mCurrentItem;
	private boolean mCurrentServiceReady;

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCurrentServiceReady = false;
		mCurrentTitle = mBaseTitle = getString(R.string.current_service);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);

		if (savedInstanceState != null) {
			// currents service data
			HashMap<String, Object> current = (HashMap<String, Object>) savedInstanceState.getParcelable("current");
			mCurrent = new ExtendedHashMap(current);
			// currently selected item (now or next dialog)
			HashMap<String, Object> currentItem = (HashMap<String, Object>) savedInstanceState
					.getParcelable("currentItem");
			mCurrentItem = new ExtendedHashMap(currentItem);
		}
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.current_service, container, false);

		mServiceName = (TextView) view.findViewById(R.id.service_name);
		mProvider = (TextView) view.findViewById(R.id.provider);
		mNowStart = (TextView) view.findViewById(R.id.event_now_start);
		mNowTitle = (TextView) view.findViewById(R.id.event_now_title);
		mNowDuration = (TextView) view.findViewById(R.id.event_now_duration);
		mNextStart = (TextView) view.findViewById(R.id.event_next_start);
		mNextTitle = (TextView) view.findViewById(R.id.event_next_title);
		mNextDuration = (TextView) view.findViewById(R.id.event_next_duration);
		mStream = (Button) view.findViewById(R.id.ButtonStream);
		mNowLayout = (LinearLayout) view.findViewById(R.id.layout_now);
		mNextLayout = (LinearLayout) view.findViewById(R.id.layout_next);

		registerOnClickListener(mNowLayout, Statics.ITEM_NOW);
		registerOnClickListener(mNextLayout, Statics.ITEM_NEXT);
		registerOnClickListener(mStream, Statics.ITEM_STREAM);

		if (mCurrent == null) {
			reload();
		} else {
			applyData(0, mCurrent);
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("currentItem", mCurrentItem);
		outState.putParcelable("current", mCurrent);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case (Statics.ITEM_RELOAD):
			reload();
			return true;
		default:
			return false;
		}
	}

	/**
	 * Register an <code>OnClickListener</code> for a view and a specific item
	 * ID (<code>ITEM_*</code> statics)
	 * 
	 * @param v
	 *            The view an OnClickListener should be registered for
	 * @param id
	 *            The id used to identify the item clicked (<code>ITEM_*</code>
	 *            statics)
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemClicked(id);
			}
		});
	}

	/**
	 * @param id
	 */
	@Override
	protected boolean onItemClicked(int id) {
		String ref;

		if (mCurrentServiceReady) {
			switch (id) {
			case Statics.ITEM_NOW:
				showEpgDetail(mNow);
				return true;
			case Statics.ITEM_NEXT:
				showEpgDetail(mNext);
				return true;
			case Statics.ITEM_STREAM:
				ref = mService.getString(Service.KEY_REFERENCE);
				if (!"".equals(ref) && ref != null) {
					streamService(ref);
				} else {
					showToast(getText(R.string.not_available));
				}
				return true;
			default:
				return false;
			}
		} else {
			showToast(getText(R.string.not_available));
			return true;
		}
	}

	private void showEpgDetail(ExtendedHashMap event) {
		if (event != null) {
			mCurrentItem = event;
			Bundle args = new Bundle();
			args.putParcelable("currentItem", mCurrentItem);
			((MultiPaneHandler) getSherlockActivity()).showDialogFragment(EpgDetailDialog.class, args,
					"current_epg_detail_dialog");
		}
	}

	/**
	 * Called after loading the current service has finished to update the
	 * GUI-Content
	 */
	@Override
	protected void applyData(int loaderId, ExtendedHashMap content) {
		if (content != null) {
			mCurrent = content;
			mCurrentServiceReady = true;

			mService = (ExtendedHashMap) mCurrent.get(CurrentService.KEY_SERVICE);
			@SuppressWarnings("unchecked")
			ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) mCurrent.get(CurrentService.KEY_EVENTS);
			mNow = events.get(0);
			mNext = events.get(1);

			mServiceName.setText(mService.getString(CurrentService.KEY_SERVICE_NAME));
			mProvider.setText(mService.getString(CurrentService.KEY_SERVICE_PROVIDER));
			// Now
			mNowStart.setText(mNow.getString(Event.KEY_EVENT_START_READABLE));
			mNowTitle.setText(mNow.getString(Event.KEY_EVENT_TITLE));
			mNowDuration.setText(mNow.getString(Event.KEY_EVENT_DURATION_READABLE));
			// Next
			mNextStart.setText(mNext.getString(Event.KEY_EVENT_START_READABLE));
			mNextTitle.setText(mNext.getString(Event.KEY_EVENT_TITLE));
			mNextDuration.setText(mNext.getString(Event.KEY_EVENT_DURATION_READABLE));
		} else {
			showToast(getText(R.string.not_available));
		}
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent((MultiPaneHandler) getSherlockActivity(), event, this);
	}

	/**
	 * @param event
	 */
	protected void setTimerById(ExtendedHashMap event) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult
	 * (boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}

	/**
	 * @param ref
	 *            A ServiceReference
	 */
	private void streamService(String ref) {
		Intent intent = IntentFactory.getStreamServiceIntent(ref);
		startActivity(intent);
	}

	@Override
	public void onDialogAction(int action, Object details) {
		switch (action) {
		case Statics.ACTION_SET_TIMER:
			setTimerById(mCurrentItem);
			break;
		case Statics.ACTION_EDIT_TIMER:
			setTimerByEventData(mCurrentItem);
			break;
		case Statics.ACTION_FIND_SIMILAR:
			findSimilarEvents(mCurrentItem);
			break;
		case Statics.ACTION_IMDB:
			IntentFactory.queryIMDb(getSherlockActivity(), mCurrentItem);
			break;
		}
	}

	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		AsyncSimpleLoader loader = new AsyncSimpleLoader(getSherlockActivity(), new CurrentServiceRequestHandler(),
				args);
		return loader;
	}
}
