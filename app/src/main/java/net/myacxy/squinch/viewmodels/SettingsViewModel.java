package net.myacxy.squinch.viewmodels;

import com.google.android.apps.dashclock.api.DashClockExtension;

import net.myacxy.retrotwitch.utils.StringUtil;
import net.myacxy.retrotwitch.v5.RxRetroTwitch;
import net.myacxy.retrotwitch.v5.api.common.RetroTwitchError;
import net.myacxy.retrotwitch.v5.api.users.SimpleUser;
import net.myacxy.retrotwitch.v5.api.users.SimpleUsersResponse;
import net.myacxy.retrotwitch.v5.api.users.UserFollow;
import net.myacxy.retrotwitch.v5.helpers.RxRetroTwitchErrorFactory;
import net.myacxy.squinch.helpers.DataHelper;
import net.myacxy.squinch.helpers.tracking.Th;
import net.myacxy.squinch.models.SettingsModel;
import net.myacxy.squinch.models.events.DashclockUpdateEvent;
import net.myacxy.squinch.utils.RetroTwitchUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

public class SettingsViewModel implements ViewModel {

    private final DataHelper dataHelper;

    public SettingsModel settings;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    public SettingsViewModel(DataHelper dataHelper) {
        this.dataHelper = dataHelper;
        settings = dataHelper.recoverSettings();
        refresh();
    }

    public void refresh() {
        settings.userError.set(null);
        settings.userLogo.set(settings.user.get() != null ? settings.user.get().getLogo() : null);
        settings.deselectedChannelIds.set(dataHelper.getDeselectedChannelIds());
        updateSelectedChannelsText(dataHelper.getUserFollows(), settings.deselectedChannelIds.get());
    }

    public Consumer<String> getUser() {
        return userName -> {

            compositeDisposable.clear();
            settings.userError.set(null);
            settings.tmpUser.set(null);

            Th.l(this, "#getUser=%s", userName);
            if (userName != null && (userName = userName.trim()).length() != 0) {
                updateSelectedChannelsText(Collections.emptyList(), Collections.emptyList());
                settings.isLoadingUser.set(true);

                Disposable disposable = RxRetroTwitch.getInstance()
                        .users()
                        .translateUserNameToUserId(userName)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(getUserObserver());

                compositeDisposable.add(disposable);
            }
        };
    }

    public void onHideExtensionChanged(boolean hide) {
        settings.isEmptyExtensionHidden.set(hide);
        dataHelper.setHideEmptyExtension(hide);
    }

    private void updateSelectedChannelsText(final List<UserFollow> userFollows, List<Long> deselectedChannelIds) {
        if (!userFollows.isEmpty()) {
            Observable.fromIterable(userFollows)
                    .filter(userFollow -> deselectedChannelIds.contains(userFollow.getChannel().getId()))
                    .count()
                    .subscribeOn(Schedulers.computation())
                    .subscribe((deselected, t) -> {
                        String text = String.format(Locale.getDefault(), "%d\u2009/\u2009%d", userFollows.size() - deselected, userFollows.size());
                        settings.selectedChannelsText.set(text);
                    });
            return;
        }
        settings.selectedChannelsText.set("0\u2009/\u20090");
    }

    private void onUserError(RetroTwitchError error) {
        Th.err(error);
        settings.user.set(null);
        settings.userFollows.get().clear();
        dataHelper.setUser(null);
        dataHelper.setUserFollows(null);

        settings.userLogo.set(null);
        settings.isLoadingUser.set(false);
        settings.userError.set(error.getMessage());
        updateSelectedChannelsText(Collections.emptyList(), settings.deselectedChannelIds.get());
    }

    private DisposableObserver<Response<SimpleUsersResponse>> getUserObserver() {
        return new DisposableObserver<Response<SimpleUsersResponse>>() {

            @Override
            public void onNext(Response<SimpleUsersResponse> response) {
                RetroTwitchError error = RxRetroTwitchErrorFactory.fromResponse(response);
                if (error != null) {
                    onUserError(error);
                    return;
                } else if (response.body().getUsers().size() == 0) {
                    onUserError(new RetroTwitchError("USER_NAME_TO_USER_ID_EMPTY", 200, "User does not exist"));
                    return;
                }

                settings.tmpUser.set(response.body().getUsers().get(0));
            }

            @Override
            public void onComplete() {
                if (settings.tmpUser.get() != null && !StringUtil.isEmpty(settings.tmpUser.get().getName())) {
                    Disposable disposable = RetroTwitchUtil.getAllUserFollows(settings.tmpUser.get().getId(),
                            progress -> {
                                updateSelectedChannelsText(progress, settings.deselectedChannelIds.get());
                                Th.l(SettingsViewModel.this, "userFollows.progress=%d", progress.size());
                            })
                            .subscribeOn(Schedulers.io())
                            .doOnError(throwable -> onUserError(RxRetroTwitchErrorFactory.fromThrowable(throwable)))
                            .doOnSuccess(userFollows -> {
                                SimpleUser user = settings.tmpUser.get();
                                settings.user.set(user);
                                settings.userFollows.set(userFollows);
                                dataHelper.setUser(user);
                                dataHelper.setUserFollows(userFollows);

                                updateSelectedChannelsText(userFollows, settings.deselectedChannelIds.get());
                                settings.userLogo.set(user.getLogo());
                                settings.isLoadingUser.set(false);
                                Th.l(SettingsViewModel.this, "#onComplete.user=%s", user);
                            })
                            .flatMap(
                                    userFollows -> RetroTwitchUtil.getAllLiveStreams(userFollows,
                                            progress -> Th.l(SettingsViewModel.this, "streams.progress=%d", progress.size()))
                            ).subscribe(
                                    streams -> {
                                        Th.l(SettingsViewModel.this, "#onComplete.streams=%d", streams.size());
                                        dataHelper.setLiveStreams(streams);
                                        EventBus.getDefault().post(new DashclockUpdateEvent(DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED));
                                    },
                                    throwable -> onUserError(RxRetroTwitchErrorFactory.fromThrowable(throwable))
                            );
                    compositeDisposable.add(disposable);
                } else {
                    settings.isLoadingUser.set(false);
                }
            }

            @Override
            public void onError(Throwable t) {
                onUserError(RxRetroTwitchErrorFactory.fromThrowable(t));
            }
        };
    }
}
