package net.myacxy.dashclock.twitch.io;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import net.myacxy.dashclock.twitch.models.TwitchGame;

import java.util.ArrayList;

public class TggManager extends AsyncTask<Void, Integer, ArrayList<TwitchGame>> {

    protected ArrayList<TwitchGameGetter> mTggs;
    protected Context mContext;
    protected ProgressDialog mProgressDialog;
    protected AsyncTaskListener mListener;
    protected ArrayList<TwitchGame> mResults;
    private int mTotal;
    private int mLimit;

    public TggManager(Context context, boolean showProgress) {
        mContext = context;
        if(showProgress) mProgressDialog = new ProgressDialog(context);
        mResults = new ArrayList<TwitchGame>();
        mTggs = new ArrayList<TwitchGameGetter>();
    }

    @Override
    protected void onPreExecute() {
        for (int offset = 0; offset < mTotal; offset += mLimit) {
            final TwitchGameGetter tgg = new TwitchGameGetter(mContext);
            mTggs.add(tgg);
            tgg.run(mLimit, offset);
        }
        if(mProgressDialog != null) {
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setMax(mLimit);
            mProgressDialog.show();
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (mProgressDialog != null) {
            mProgressDialog.setProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(ArrayList<TwitchGame> games) {
        TwitchDbHelper dbHelper = new TwitchDbHelper(mContext);
        dbHelper.updateOrReplaceGameEntries(games);

        if (mProgressDialog != null) mProgressDialog.dismiss();
        if (mListener != null) mListener.handleAsyncTaskFinished();
    }

    @Override
    protected ArrayList<TwitchGame> doInBackground(Void... params) {
        while (true) {
            for (TwitchGameGetter tgg : mTggs) {
                if (tgg.getStatus() == Status.FINISHED && !mResults.contains(tgg.games.get(0))) {
                    for(TwitchGame game : tgg.games) {
                        mResults.add((game));
                        publishProgress(mResults.size());
                    }
                }
            }
            if (mResults.size() == mLimit) break;
        }
        Log.d("TggManager", "doInBackground finished");
        return mResults;
    }

    public void run(int total, int limit) {
        mTotal = total;
        mLimit = limit;
        executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    public void setAsyncTaskListener(AsyncTaskListener asyncTaskListener) {
        mListener = asyncTaskListener;
    }
}