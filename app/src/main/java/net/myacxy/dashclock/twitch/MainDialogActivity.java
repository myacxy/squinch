/**
 * Copyright (c) 2014, Johannes Hoffmann
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.myacxy.dashclock.twitch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TabHost;
import android.widget.TextView;

import net.myacxy.dashclock.twitch.database.ChannelQuery;
import net.myacxy.dashclock.twitch.database.TwitchContract;
import net.myacxy.dashclock.twitch.database.TwitchDbHelper;
import net.myacxy.dashclock.twitch.io.AsyncTaskListener;
import net.myacxy.dashclock.twitch.io.TwitchUserFollowsGetter;
import net.myacxy.dashclock.twitch.models.TwitchGame;

import java.util.ArrayList;

public class MainDialogActivity extends Activity {

    protected TabHost mTabHost;
    protected SharedPreferences mSharedPreferences;
    protected TwitchDbHelper mDbHelper;
    protected Cursor mCursor;
    private TwitchUserFollowsGetter mFollowsGetter;

    private ArrayList<String> rowKeys = new ArrayList<String>() {{
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_NAME);
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_GAME);
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_STATUS);
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_VIEWERS);
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_FOLLOWERS);
        add(TwitchExtension.PREF_MAIN_LIST_SHOW_UPDATED);
    }};

    private ArrayList<Integer> rowIds = new ArrayList<Integer>() {{
        add(R.id.main_list_item_display_name_text);
        add(R.id.main_list_item_row_game);
        add(R.id.main_list_item_row_status);
        add(R.id.main_list_item_row_viewers);
        add(R.id.main_list_item_row_followers);
        add(R.id.main_list_item_row_updated);
    }};

    private ArrayList<Integer> queryIds = new ArrayList<Integer>() {{
        add(ChannelQuery.displayName);
        add(ChannelQuery.gameId);
        add(ChannelQuery.status);
        add(ChannelQuery.viewers);
        add(ChannelQuery.followers);
        add(ChannelQuery.updatedAt);
    }};

    private ArrayList<Integer> textIds = new ArrayList<Integer>() {{
        add(R.id.main_list_item_display_name_text);
        add(R.id.main_list_item_game_text);
        add(R.id.main_list_item_status_text);
        add(R.id.main_list_item_viewers_text);
        add(R.id.main_list_item_followers_text);
        add(R.id.main_list_item_updated_text);
    }};



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showAsPopup(this);
        setContentView(R.layout.main);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.addTab(mTabHost.newTabSpec("online")
                .setIndicator("Online")
                .setContent(R.id.main_tab_online));
        mTabHost.addTab(mTabHost.newTabSpec("offline")
                .setIndicator("Offline")
                .setContent(R.id.main_tab_offline));

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                switch (tabId) {
                    case "online":
                        initView((ListView) findViewById(R.id.main_tab_online_list),
                                TwitchDbHelper.State.ONLINE);
                        break;
                    case "offline":
                        initView((ListView) findViewById(R.id.main_tab_offline_list),
                                TwitchDbHelper.State.OFFLINE);
                        break;
                }
            }
        });
        mTabHost.setCurrentTabByTag("offline");
        mTabHost.setCurrentTabByTag("online");
    }

    public void showAsPopup(Activity activity) {
        //To show activity as dialog and dim the background, you need to declare android:theme="@style/PopupTheme" on for the chosen activity on the manifest
        activity.requestWindowFeature(Window.FEATURE_ACTION_BAR);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.alpha = 1.0f;
        params.dimAmount = 0.5f;
        activity.getWindow().setAttributes(params);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Button button = (Button) findViewById(R.id.main_dismiss);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_update: {
                mFollowsGetter = TwitchExtension.updateTwitchChannels(this,
                        true,
                        new AsyncTaskListener() {
                            @Override
                            public void handleAsyncTaskFinished() {
                                Log.d("MainDialog", "Update finished.");
                                if (getApplicationContext() != null) {
                                    // reinit view and update data
                                    new TwitchDbHelper(getApplicationContext())
                                            .updatePublishedData();
                                    mTabHost.setCurrentTabByTag("offline");
                                    mTabHost.setCurrentTabByTag("online");
                                }
                            }
                        });
                break;
            }
            case R.id.action_settings: {
                startActivity(new Intent(this, TwitchSettingsActivity.class));
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void initView(ListView listView, TwitchDbHelper.State state) {

        // initialize database
        boolean selected = mSharedPreferences.getBoolean(TwitchExtension.PREF_CUSTOM_VISIBILITY, false);
        String sortOrder = TwitchContract.ChannelEntry.COLUMN_NAME_NAME;
        mDbHelper = new TwitchDbHelper(this);
        // get cursor for the channels that are online
        mCursor = mDbHelper.getChannelsCursor(selected, state, sortOrder);
        // add online list adapter
        ListAdapter listAdapter = new ListAdapter(this);
        listAdapter.swapCursor(mCursor);
        listView.setAdapter(listAdapter);
    }

    public class ListAdapter extends ResourceCursorAdapter
    {
        public ListAdapter(Context context)
        {
            // inflate row layout
            super(context, R.layout.list_item_main, null, false);
        }

        /** Set elements of each row */
        @Override
        public void bindView(final View view, Context context, final Cursor cursor) {

            for(String key : rowKeys) {
                final int index = rowKeys.indexOf(key);
                boolean visible = mSharedPreferences.getBoolean(key, true);
                if(!visible) view.findViewById(rowIds.get(index)).setVisibility(View.GONE);

                if(queryIds.get(index) == ChannelQuery.gameId) {
                    TextView gameView = (TextView) view.findViewById(R.id.main_list_item_game_text);
                    TwitchGame game = mDbHelper.getGame(cursor.getInt(ChannelQuery.gameId));
                    gameView.setText(game.name);
                }
                else if (queryIds.get(index) == ChannelQuery.displayName)
                {
                    view.setTag(cursor.getString(ChannelQuery.name));

                    TextView textView = (TextView) view.findViewById(textIds.get(index));
                    textView.setText(cursor.getString(queryIds.get(index)));
                } else {
                    TextView textView = (TextView) view.findViewById(textIds.get(index));
                    textView.setText(cursor.getString(queryIds.get(index)));
                }

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String url = "http://www.twitch.tv/" + view.getTag().toString();
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                });
            }
        } // bindView
    } // ListAdapter

    @Override
    public void onDestroy() {
        mDbHelper.close();
        mCursor.close();
        // cancel async tasks
        if(mFollowsGetter == null);
        else if(mFollowsGetter.getStatus() != AsyncTask.Status.FINISHED)
            mFollowsGetter.cancel(true);
        else if(mFollowsGetter.tcocManager != null)
            if(mFollowsGetter.tcocManager.getStatus() != AsyncTask.Status.FINISHED)
                mFollowsGetter.tcocManager.cancel(true);

        super.onDestroy();
    }
}