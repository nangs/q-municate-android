package com.quickblox.q_municate.ui.activities.base;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import com.facebook.Session;
import com.quickblox.auth.model.QBProvider;
import com.quickblox.q_municate.App;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.core.bridges.ActionBarBridge;
import com.quickblox.q_municate.core.bridges.ConnectionBridge;
import com.quickblox.q_municate.core.bridges.LoadingBridge;
import com.quickblox.q_municate.core.gcm.GCMIntentService;
import com.quickblox.q_municate.core.listeners.ServiceConnectionListener;
import com.quickblox.q_municate.core.listeners.UserStatusChangingListener;
import com.quickblox.q_municate.ui.activities.authorization.SplashActivity;
import com.quickblox.q_municate.ui.fragments.dialogs.base.ProgressDialogFragment;
import com.quickblox.q_municate.ui.activities.call.CallActivity;
import com.quickblox.q_municate.utils.ToastUtils;
import com.quickblox.q_municate.utils.helpers.SharedHelper;
import com.quickblox.q_municate.utils.helpers.ActivityUIHelper;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.models.LoginType;
import com.quickblox.q_municate_core.qb.commands.QBLoginRestCommand;
import com.quickblox.q_municate_core.qb.commands.QBSocialLoginCommand;
import com.quickblox.q_municate_core.qb.helpers.QBFriendListHelper;
import com.quickblox.q_municate_core.qb.helpers.QBGroupChatHelper;
import com.quickblox.q_municate_core.qb.helpers.QBPrivateChatHelper;
import com.quickblox.q_municate_core.service.QBService;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.ErrorUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import butterknife.ButterKnife;
import de.keyboardsurfer.android.widget.crouton.Crouton;

public abstract class BaseActivity extends AppCompatActivity implements ActionBarBridge, ConnectionBridge, LoadingBridge {

    protected App app;
    protected Toolbar toolbar;
    protected SharedHelper appSharedHelper;
    protected Fragment currentFragment;
    protected FailAction failAction;
    protected SuccessAction successAction;
    protected QBFriendListHelper friendListHelper;
    protected QBPrivateChatHelper privateChatHelper;
    protected QBGroupChatHelper groupChatHelper;
    protected QBService service;
    protected LocalBroadcastManager localBroadcastManager;

    private ActionBar actionBar;
    private Map<String, Set<Command>> broadcastCommandMap;
    private Set<UserStatusChangingListener> fragmentsStatusChangingSet;
    private Set<ServiceConnectionListener> fragmentsServiceConnectionSet;
    private Handler handler;
    private BaseBroadcastReceiver broadcastReceiver;
    private GlobalBroadcastReceiver globalBroadcastReceiver;
    private UserStatusBroadcastReceiver userStatusBroadcastReceiver;
    private boolean bounded;
    private ServiceConnection serviceConnection;
    private ActivityUIHelper activityUIHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initFields();
    }

    private void initFields() {
        app = App.getInstance();
        appSharedHelper = App.getInstance().getAppSharedHelper();
        activityUIHelper = new ActivityUIHelper(this);
        failAction = new FailAction();
        successAction = new SuccessAction();
        broadcastReceiver = new BaseBroadcastReceiver();
        globalBroadcastReceiver = new GlobalBroadcastReceiver();
        userStatusBroadcastReceiver = new UserStatusBroadcastReceiver();
        broadcastCommandMap = new HashMap<>();
        fragmentsStatusChangingSet = new HashSet<>();
        fragmentsServiceConnectionSet = new HashSet<>();
        serviceConnection = new QBChatServiceConnection();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public void initActionBar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        actionBar = getSupportActionBar();
    }

    @Override
    public void setActionBarTitle(String title) {
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    @Override
    public void setActionBarTitle(@StringRes int title) {
        setActionBarTitle(getString(title));
    }

    @Override
    public void setActionBarSubtitle(String subtitle) {
        if (actionBar != null) {
            actionBar.setSubtitle(subtitle);
        }
    }

    @Override
    public void setActionBarSubtitle(@StringRes int subtitle) {
        setActionBarSubtitle(getString(subtitle));
    }

    @Override
    public void setActionBarIcon(Drawable icon) {
        if (actionBar != null) {
            // In appcompat v21 there will be no icon if we don't add this display option
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setIcon(icon);
        }
    }

    @Override
    public void setActionBarIcon(@DrawableRes int icon) {
        Drawable drawable;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawable = getDrawable(icon);
        } else {
            drawable = getResources().getDrawable(icon);
        }

        setActionBarIcon(drawable);
    }

    @Override
    public void setActionBarUpButtonEnabled(boolean enabled) {
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(enabled);
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }

    @Override
    public synchronized void showProgress() {
        ProgressDialogFragment.show(getSupportFragmentManager());
    }

    @Override
    public synchronized void hideProgress() {
        ProgressDialogFragment.hide(getSupportFragmentManager());
    }

    @Override
    public void hideActionBarProgress() {
        setVisibilityActionBarProgress(false);
    }

    @Override
    public void showActionBarProgress() {
        setVisibilityActionBarProgress(true);
    }

    @Override
    public boolean checkNetworkAvailableWithError() {
        // TODO network checking here
        return false;
    }

    @Override
    public boolean isNetworkAvailable() {
        // TODO network checking here
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectToService();
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerBroadcastReceivers();
        updateBroadcastActionList();

        addAction(QBServiceConsts.LOGIN_REST_SUCCESS_ACTION, successAction);

        GCMIntentService.clearNotificationEvent(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterBroadcastReceivers();
        Crouton.cancelAllCroutons();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateToParent();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void addFragmentUserStatusChangingListener(
            UserStatusChangingListener fragmentUserStatusChangingListener) {
        if (fragmentsStatusChangingSet == null) {
            fragmentsStatusChangingSet = new HashSet<>();
        }
        fragmentsStatusChangingSet.add(fragmentUserStatusChangingListener);
    }

    public void removeFragmentUserStatusChangingListener(
            UserStatusChangingListener fragmentUserStatusChangingListener) {
        fragmentsStatusChangingSet.remove(fragmentUserStatusChangingListener);
    }

    public void addFragmentServiceConnectionListener (
            ServiceConnectionListener fragmentServiceConnectionListener) {
        if (fragmentsServiceConnectionSet == null) {
            fragmentsServiceConnectionSet = new HashSet<>();
        }
        fragmentsServiceConnectionSet.add(fragmentServiceConnectionListener);
    }

    public void removeFragmentServiceConnectionListener (
            ServiceConnectionListener fragmentServiceConnectionListener) {
        fragmentsServiceConnectionSet.remove(fragmentServiceConnectionListener);
    }

    public void notifyChangedUserStatus(int userId, boolean online) {
        if (!fragmentsStatusChangingSet.isEmpty()) {
            Iterator<UserStatusChangingListener> iterator = fragmentsStatusChangingSet.iterator();
            while (iterator.hasNext()) {
                iterator.next().onChangedUserStatus(userId, online);
            }
        }
    }

    public void notifyConnectedToService() {
        if (!fragmentsServiceConnectionSet.isEmpty()) {
            Iterator<ServiceConnectionListener> iterator = fragmentsServiceConnectionSet.iterator();
            while (iterator.hasNext()) {
                iterator.next().onConnectedToService(service);
            }
        }
    }

    public void onConnectedToService(QBService service) {
        if (friendListHelper == null) {
            friendListHelper = (QBFriendListHelper) service.getHelper(QBService.FRIEND_LIST_HELPER);
        }

        if (privateChatHelper == null) {
            privateChatHelper = (QBPrivateChatHelper) service.getHelper(QBService.PRIVATE_CHAT_HELPER);
        }

        if (groupChatHelper == null) {
            groupChatHelper = (QBGroupChatHelper) service.getHelper(QBService.GROUP_CHAT_HELPER);
        }

        notifyConnectedToService();
    }

    private void unbindService() {
        if (bounded) {
            unbindService(serviceConnection);
        }
    }

    private void connectToService() {
        Intent intent = new Intent(this, QBService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void registerBroadcastReceivers() {
        IntentFilter globalActionsIntentFilter = new IntentFilter();
        globalActionsIntentFilter.addAction(QBServiceConsts.GOT_CHAT_MESSAGE);
        globalActionsIntentFilter.addAction(QBServiceConsts.GOT_CONTACT_REQUEST);
        globalActionsIntentFilter.addAction(QBServiceConsts.FORCE_RELOGIN);
        globalActionsIntentFilter.addAction(QBServiceConsts.REFRESH_SESSION);
        globalActionsIntentFilter.addAction(QBServiceConsts.TYPING_MESSAGE);
        localBroadcastManager.registerReceiver(globalBroadcastReceiver, globalActionsIntentFilter);

        localBroadcastManager.registerReceiver(userStatusBroadcastReceiver,
                new IntentFilter(QBServiceConsts.USER_STATUS_CHANGED_ACTION));
    }

    private void unregisterBroadcastReceivers() {
        localBroadcastManager.unregisterReceiver(globalBroadcastReceiver);
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
        localBroadcastManager.unregisterReceiver(userStatusBroadcastReceiver);
    }

    private void navigateToParent() {
        Intent intent = NavUtils.getParentActivityIntent(this);
        if (intent == null) {
            finish();
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            NavUtils.navigateUpTo(this, intent);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T _findViewById(int viewId) {
        return (T) findViewById(viewId);
    }

    public void setCurrentFragment(Fragment fragment) {
        currentFragment = fragment;
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction transaction = buildTransaction();
        transaction.replace(R.id.container_fragment, fragment, null);
        transaction.commit();
    }

    private FragmentTransaction buildTransaction() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        return transaction;
    }

    private boolean needShowReceivedNotification() {
        boolean isSplashActivity = this instanceof SplashActivity;
        boolean isCallActivity = this instanceof CallActivity;
        return !isSplashActivity && !isCallActivity;
    }

    protected void onSuccessAction(String action) {
    }

    protected void onFailAction(String action) {
    }

    protected void onReceivedChatMessageNotification(Bundle extras) {
        activityUIHelper.showChatMessageNotification(extras);
    }

    protected void onReceivedContactRequestNotification(Bundle extras) {
        activityUIHelper.showContactRequestNotification(extras);
    }

    public void forceRelogin() {
        ErrorUtils.showError(this, getString(R.string.dlg_force_relogin_on_token_required));
        SplashActivity.start(this);
        finish();
    }

    public void refreshSession() {
        if (LoginType.EMAIL.equals(AppSession.getSession().getLoginType())) {
            QBLoginRestCommand.start(this, AppSession.getSession().getUser());
        } else {
            QBSocialLoginCommand.start(this, QBProvider.FACEBOOK, Session.getActiveSession().getAccessToken(),
                    null);
        }
    }

    private Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    public void setVisibilityActionBarProgress(boolean visibility) {
        setProgressBarIndeterminateVisibility(visibility);
    }

    public void addAction(String action, Command command) {
        Set<Command> commandSet = broadcastCommandMap.get(action);
        if (commandSet == null) {
            commandSet = new HashSet<Command>();
            broadcastCommandMap.put(action, commandSet);
        }
        commandSet.add(command);
    }

    public boolean hasAction(String action) {
        return broadcastCommandMap.containsKey(action);
    }

    public void removeAction(String action) {
        broadcastCommandMap.remove(action);
    }

    public void updateBroadcastActionList() {
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
        IntentFilter intentFilter = new IntentFilter();
        for (String commandName : broadcastCommandMap.keySet()) {
            intentFilter.addAction(commandName);
        }
        localBroadcastManager.registerReceiver(broadcastReceiver, intentFilter);
    }

    public void onReceiveChatMessageAction(Bundle extras) {
        if (needShowReceivedNotification()) {
            onReceivedChatMessageNotification(extras);
        }
    }

    public void onReceiveForceReloginAction(Bundle extras) {
        forceRelogin();
    }

    public void onReceiveRefreshSessionAction(Bundle extras) {
        ToastUtils.longToast(R.string.dlg_refresh_session);
        refreshSession();
    }

    public void onReceiveContactRequestAction(Bundle extras) {
        if (needShowReceivedNotification()) {
            onReceivedContactRequestNotification(extras);
        }
    }

    public QBService getService() {
        return service;
    }

    public QBFriendListHelper getFriendListHelper() {
        return friendListHelper;
    }

    public QBPrivateChatHelper getPrivateChatHelper() {
        return privateChatHelper;
    }

    public QBGroupChatHelper getGroupChatHelper() {
        return groupChatHelper;
    }

    public FailAction getFailAction() {
        return failAction;
    }

    protected void activateButterKnife() {
        ButterKnife.bind(this);
    }

    public class FailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception e = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
            ErrorUtils.showError(BaseActivity.this, e);
            hideProgress();
            onFailAction(bundle.getString(QBServiceConsts.COMMAND_ACTION));
        }
    }

    public class SuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            hideProgress();
            onSuccessAction(bundle.getString(QBServiceConsts.COMMAND_ACTION));
        }
    }

    private class BaseBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();
            if (intent != null && (action) != null) {
                Log.d("STEPS", "executing " + action);
                final Set<Command> commandSet = broadcastCommandMap.get(action);

                if (commandSet != null && !commandSet.isEmpty()) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            for (Command command : commandSet) {
                                try {
                                    command.execute(intent.getExtras());
                                } catch (Exception e) {
                                    ErrorUtils.logError(e);
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    private class GlobalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, final Intent intent) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    Bundle extras = intent.getExtras();
                    if (extras != null && QBServiceConsts.GOT_CHAT_MESSAGE.equals(intent.getAction())) {
                        onReceiveChatMessageAction(intent.getExtras());
                    } else if (QBServiceConsts.GOT_CONTACT_REQUEST.equals(intent.getAction())) {
                        onReceiveContactRequestAction(intent.getExtras());
                    } else if (QBServiceConsts.FORCE_RELOGIN.equals(intent.getAction())) {
                        onReceiveForceReloginAction(intent.getExtras());
                    } else if (QBServiceConsts.REFRESH_SESSION.equals(intent.getAction())) {
                        onReceiveRefreshSessionAction(intent.getExtras());
                    }
                }
            });
        }
    }

    private class UserStatusBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int userId = intent.getIntExtra(QBServiceConsts.EXTRA_USER_ID, 0);
            boolean status = intent.getBooleanExtra(QBServiceConsts.EXTRA_USER_STATUS, false);
            notifyChangedUserStatus(userId, status);
        }
    }

    private class QBChatServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bounded = true;
            service = ((QBService.QBServiceBinder) binder).getService();
            onConnectedToService(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }
}