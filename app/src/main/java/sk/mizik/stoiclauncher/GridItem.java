package sk.mizik.stoiclauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

// SIMPLE POJO FOR ONE GRID ITEM REPRESENTING AN APP
public class GridItem {
    private String name;
    private String packageName;
    private Drawable icon;
    private int position;
    private Intent launchIntent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Intent getLaunchIntent() {
        return launchIntent;
    }

    public void setLaunchIntent(Intent launchIntent) {
        this.launchIntent = launchIntent;
    }
}
