package de.htw_berlin.imi.s0527535.dashclocktwitch;

import android.app.ProgressDialog;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

public class TwitchOnlineChecker extends JsonGetter
{
    protected TwitchChannel mTwitchChannel;
    protected boolean mDismissProgressDialog;

    public TwitchOnlineChecker(Context context) {
        super(context);
    }

    public TwitchOnlineChecker(Context context, ProgressDialog progressDialog)
    {
        super(context);
        mProgressDialog = progressDialog;
    }

    @Override
    protected void onPreExecute() {

    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
        // check if stream is online
        try {
            if (jsonObject.getString("stream").equals("null")) {
                mTwitchChannel.online = false;
            } else {
                mTwitchChannel.online = true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // update database
        TwitchDbHelper twitchDbHelper = new TwitchDbHelper(mContext);
        twitchDbHelper.updateOnlineStatus(mTwitchChannel);
        // dismiss progress dialog
        if(mDismissProgressDialog) mProgressDialog.dismiss();
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        // display current progress
        mProgressDialog.setMessage("Checking " + values[0] + "...");
    }

    @Override
    protected JSONObject doInBackground(String... params) {
        publishProgress(mTwitchChannel.displayName);
        return super.doInBackground(params);
    }

    /**
     * Retrieve the JSON data of the stream in order to check if the streamer is currently online.
     *
     * @param twitchChannel Stream to be checked.
     */
    public void run(TwitchChannel twitchChannel, boolean dismiss)
    {
        mDismissProgressDialog = dismiss;
        mTwitchChannel = twitchChannel;
        String url = "https://api.twitch.tv/kraken/streams/" + twitchChannel.displayName;
        // execute tasks one after the other
        executeOnExecutor(SERIAL_EXECUTOR, url);
    }
}
