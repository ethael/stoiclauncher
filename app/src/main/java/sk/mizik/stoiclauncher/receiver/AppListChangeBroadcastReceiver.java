package sk.mizik.stoiclauncher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import sk.mizik.stoiclauncher.MainActivity;

/**
 * RECEIVER LISTENING FOR CHANGES IN INSTALLED APP LIST (ADD, REMOVE, CHANGE, REPLACE)
 * IF SUCH ACTION HAPPENS, GUI WILL BE REFRESHED
 */
public class AppListChangeBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                Intent.ACTION_PACKAGE_CHANGED.equals(action) ||
                Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)
        ) {
            MainActivity.getInstance().initUI();
        }
    }
}