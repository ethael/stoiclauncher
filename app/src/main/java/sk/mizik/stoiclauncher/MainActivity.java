package sk.mizik.stoiclauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sk.mizik.stoiclauncher.receiver.AppListChangeBroadcastReceiver;

public class MainActivity extends AppCompatActivity {

    // ID FOR LOGGING AND SHARED PREFERENCES
    public static final String APP_NAME = "STOICLAUNCHER";
    // ID FOR SHARED PREFERENCES WHERE WE STORE HOME GRID DATA
    public static final String SP_HOME_APPS_ID = "SP_HOME_APPS_ID";
    // ID FOR SHARED PREFERENCES WHERE WE STORE GRID ROWS VALUE
    public static final String SP_GRID_ROWS_ID = "SP_GRID_ROWS_ID";
    // ID FOR SHARED PREFERENCES WHERE WE STORE GRID COLUMNS VALUE
    public static final String SP_GRID_COLUMNS_ID = "SP_GRID_COLUMNS_ID";
    // GRID ITEM HEIGHT
    private int cellHeight;
    // HEIGHT OF THE BOTTOM SHEET SHOWING THE "ALL APP" GRID
    private int bottomSheetHeight;
    // BOTTOM SHEET CONTROLLER
    BottomSheetBehavior<View> bottomSheetBehavior;
    // ALL APPS GRID VIEW
    GridView allAppsGridView;
    // HOME GRID VIEW
    GridView homeGridView;
    // ALL APPS GRID ADAPTER
    GridAdapter allAppsGridAdapter;
    // HOME GRID ADAPTER
    GridAdapter homeGridAdapter;
    // BOTTOM SHEET DRAG HANDLE LAYOUT
    LinearLayout bottomSheetDragHandle;
    // DATA FOR "ALL APPS" GRID
    Map<Integer, GridItem> allAppsGridItems = new HashMap<>();
    // CURRENTLY SELECTED ITEM TO BE MOVED
    private GridItem movingGridItem;
    // WHETHER THE CURRENTLY SELECTED ITEM TO BE MOVED WAS PICKED FROM HOME GRID OR "ALL APPS" GRID
    private Boolean movingInsideHomeGrid;
    // INSTALL/UNINSTALL APP BROADCAST RECEIVER
    private BroadcastReceiver appListChangeReceiver;
    // FILE PICKER
    ActivityResultLauncher<Intent> filePickerLauncher;
    // APP SETTINGS DIALOG
    private Dialog settingsDialog;
    // APP SETTINGS
    private Settings settings;
    // STATIC SELF WEAK REFERENCE (FOR ACCESSING MainActivity FROM RECEIVER)
    private static WeakReference<MainActivity> instance;

    public static MainActivity getInstance() {
        return instance.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // INIT UI
        initUI();
        // INIT BACK PRESS HANDLER
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
        // INIT FILE PICKER
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        Log.w(APP_NAME, "Selected File: " + uri);
                        Settings settingsFromFile = loadSettingsFile(uri);
                        Log.w(APP_NAME, "Settings: " + settingsFromFile.getGridRows());
                        saveSettings(settingsFromFile);
                        initUI();
                        settingsDialog.dismiss();
                    }
                }
        );
        // INIT STATIC SELF REFERENCE
        instance = new WeakReference<>(this);
    }

    public void initUI() {
        // LOAD CURRENTLY INSTALLED APPS
        allAppsGridItems = loadInstalledApps();
        // LOAD SETTINGS
        settings = loadSettings();
        // CALCULATE BOTTOM SHEET DYNAMIC MEASURES AND INIT GRIDS
        bottomSheetDragHandle = findViewById(R.id.bottomSheetDragHandle);
        bottomSheetDragHandle.post(() -> {
            bottomSheetHeight = bottomSheetDragHandle.getHeight();
            cellHeight = (getDisplayContentHeight() - bottomSheetHeight) / settings.getGridRows();
            initHomeGrid();
            initAllAppsGrid();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // INIT BROADCAST RECEIVER
        appListChangeReceiver = new AppListChangeBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        getApplicationContext().registerReceiver(appListChangeReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getApplicationContext().unregisterReceiver(appListChangeReceiver);
    }

    private void initHomeGrid() {
        // LOAD HOME GRID DATA FROM SHARED PREFS. IF SOME POSITION IS NOT SET, GENERATE PLACEHOLDER
        Map<Integer, GridItem> homeGrid = new HashMap<>();
        for (int i = 0; i < settings.getGridColumns() * settings.getGridRows(); i++) {
            if (settings.getHomeApps().containsKey(i)) {
                homeGrid.put(i, settings.getHomeApps().get(i));
            } else {
                homeGrid.put(i, generatePlaceholder());
            }
        }
        // INIT GRID ADAPTER
        homeGridAdapter = new GridAdapter(this, homeGrid, cellHeight, true);
        homeGridView = findViewById(R.id.homeGrid);
        homeGridView.setNumColumns(settings.getGridColumns());
        homeGridView.setAdapter(homeGridAdapter);
    }

    private void initAllAppsGrid() {
        // LOAD APP GRID BASED ON ALL INSTALLED NON SYSTEM APPS AND INITIALIZE GRID ADAPTER
        allAppsGridAdapter = new GridAdapter(this, allAppsGridItems, cellHeight, false);
        allAppsGridView = findViewById(R.id.appGrid);
        allAppsGridView.setNumColumns(settings.getGridColumns());
        allAppsGridView.setAdapter(allAppsGridAdapter);
        // CONFIGURE SWIPE-UP BOTTOM SHEET CONTAINING THE "ALL APPS" GRID
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet));
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setDraggable(true);
        bottomSheetBehavior.setPeekHeight(bottomSheetHeight);
        bottomSheetBehavior.setGestureInsetBottomIgnored(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void onGridItemPressed(GridItem app, int position, boolean fromHome) {
        // GRID ITEM CLICK HOOK. BOUND TO EVERY GRID ITEM (ON BOTH HOME AND "ALL APPS" GRID)
        if (fromHome && movingGridItem != null) {
            // IF CLICKED ON HOME GRID ITEM AND THE movingGridItem IS SET, THEN THE CLICK IS
            // MARKING OF THE DESTINATION WHERE movingGridItem SHOULD BE MOVED. SO MOVE THE ITEM.
            app.setName(movingGridItem.getName());
            app.setPackageName(movingGridItem.getPackageName());
            app.setIcon(movingGridItem.getIcon());
            app.setLaunchIntent(movingGridItem.getLaunchIntent());
            app.setPosition(position);
            homeGridAdapter.apps.put(position, app);
            settings.getHomeApps().put(position, app);
            if (movingInsideHomeGrid) {
                // IF movingGridItem WAS CHOSEN FROM INSIDE THE HOME GRID, THEN DON'T COPY, BUT MOVE
                GridItem item = (GridItem) homeGridAdapter.getItem(movingGridItem.getPosition());
                item.setName("");
                item.setPackageName(null);
                item.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.gridsquare, null));
                item.setLaunchIntent(null);
                item.setPosition(movingGridItem.getPosition());
                homeGridAdapter.apps.put(movingGridItem.getPosition(), item);
                settings.getHomeApps().put(movingGridItem.getPosition(), item);
            }
            // CLEAR "MOVING ACTION" VARIABLES
            movingGridItem = null;
            movingInsideHomeGrid = null;
            // SAVE NEW STRUCTURE OF HOME GRID TO SHARED PREFERENCES
            saveSettings(new Settings(
                    settings.getGridRows(),
                    settings.getGridColumns(),
                    settings.getHomeApps())
            );
            // TELL ADAPTER TO UPDATE GRID VIEW
            homeGridAdapter.notifyDataSetChanged();
        } else {
            // IF CLICKED ON ITEM IN "ALL APPS" GRID OR ON HOME GRID WITH movingGridItem BEING NULL,
            // THEN IT IS NORMAL ICON CLICK. WE SHOULD HIDE THE BOTTOM SHEET AND START THE APP
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            if (app.getLaunchIntent() != null) {
                getApplicationContext().startActivity(app.getLaunchIntent());
            }
        }
    }

    public void onGridItemLongPressed(GridItem app, boolean fromHome, View v) {
        // GRID ITEM LONG PRESS HOOK. BOUND TO EVERY GRID ITEM (ON BOTH HOME AND "ALL APPS" GRIDS)
        if (app.getPackageName() != null && !app.getPackageName().isEmpty()) {
            // IF PACKAGE NAME IS PRESENT (USER DOESN'T CLICKED ON EMPTY POSITION WITH PLACEHOLDER,
            // BUT ON POSITION WITH REAL APP, THEN SHOW CONTEXT MENU. POPUP MENU CONTEXT NEEDS TO BE
            // ACTIVITY AND NOT APPLICATION. OTHERWISE CUSTOM MENU STYLE (FROM STYLES.XML) WON'T BE
            // APPLIED
            PopupMenu contextMenu = new PopupMenu(this, v);
            contextMenu.inflate(R.menu.context_menu);
            contextMenu.setOnMenuItemClickListener(item -> {
                        String menuItem = Objects.requireNonNull(item.getTitle()).toString();
                        if (menuItem.equals(getString(R.string.info))) {
                            // SHOW SYSTEM APP INFO SCREEN
                            Intent infoIntent = new Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            );
                            infoIntent.setData(Uri.parse("package:" + app.getPackageName()));
                            startActivity(infoIntent);
                        } else if (menuItem.equals(getString(R.string.move))) {
                            // BEGIN COPY/MOVE ACTION THAT SHOULD BE FOLLOWED BY CLICKING ON
                            // NEW POSITION IN HOME GRID. CLICKING ON "ALL APPS" GRID WILL BE
                            // IGNORED, AS IT IS READONLY
                            allAppsGridView.setY(bottomSheetHeight);
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                            movingGridItem = app;
                            movingInsideHomeGrid = fromHome;
                        } else if (menuItem.equals(getString(R.string.remove))) {
                            // REMOVE FROM GRID. THIS ACTION IS IGNORED ON "ALL APPS" GRID
                            // BECAUSE IT IS READONLY
                            if (fromHome) {
                                GridItem i = (GridItem) homeGridAdapter.getItem(app.getPosition());
                                i.setName("");
                                i.setPackageName("");
                                i.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.gridsquare, null));
                                i.setLaunchIntent(null);
                                i.setPosition(app.getPosition());
                                homeGridAdapter.apps.put(app.getPosition(), i);
                                settings.getHomeApps().put(app.getPosition(), i);
                                // SAVE NEW STRUCTURE OF HOME GRID TO SHARED PREFERENCES
                                saveSettings(new Settings(
                                        settings.getGridRows(),
                                        settings.getGridColumns(),
                                        settings.getHomeApps()
                                ));
                                // TELL ADAPTER TO UPDATE GRID VIEW
                                homeGridAdapter.notifyDataSetChanged();
                            }
                        } else if (menuItem.equals(getString(R.string.uninstall))) {
                            // SPAWN THE UNINSTALL CONFIRMATION DIALOG
                            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                            uninstallIntent.setData(Uri.parse("package:" + app.getPackageName()));
                            startActivity(uninstallIntent);
                        } else if (menuItem.equals(getString(R.string.launcher_settings))) {
                            // SPAWN THE SETTINGS DIALOG
                            showSettingsDialog();
                        }
                        return false;
                    }
            );
            contextMenu.show();
        } else {
            showSettingsDialog();
        }
    }

    // SETTINGS DIALOG WITH IMPORT/EXPORT FUNCTIONALITY
    private void showSettingsDialog() {
        settingsDialog = new Dialog(this);
        settingsDialog.setContentView(R.layout.settings);
        // SET DIALOG SIZE TO 50% OF THE SCREEN DIMENSIONS
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Objects.requireNonNull(settingsDialog.getWindow()).setLayout(
                metrics.widthPixels,
                (int) (metrics.heightPixels * 0.5)
        );
        // INIT UI ELEMENTS
        EditText rowsField = settingsDialog.findViewById(R.id.rowsField);
        rowsField.setText(String.valueOf(settings.getGridRows()));
        EditText columnsField = settingsDialog.findViewById(R.id.columnsField);
        columnsField.setText(String.valueOf(settings.getGridColumns()));
        Button exportButton = settingsDialog.findViewById(R.id.exportButton);
        Button importButton = settingsDialog.findViewById(R.id.importButton);
        Button saveButton = settingsDialog.findViewById(R.id.saveButton);
        Button cancelButton = settingsDialog.findViewById(R.id.cancelButton);
        // EXPORT LOGIC
        exportButton.setOnClickListener(v -> {
            saveToDownloads(settings);
            settingsDialog.dismiss();
        });
        // IMPORT LOGIC
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/plain");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(Intent.createChooser(intent, "Select a file"));
        });
        // SAVE LOGIC
        saveButton.setOnClickListener(v -> {
            int rows = Integer.parseInt(rowsField.getText().toString());
            int columns = Integer.parseInt(columnsField.getText().toString());
            saveSettings(new Settings(rows, columns, settings.getHomeApps()));
            initUI();
            settingsDialog.dismiss();
            Snackbar.make(findViewById(android.R.id.content), "Settings saved", Snackbar.LENGTH_SHORT).show();
        });
        // CANCEL LOGIC
        cancelButton.setOnClickListener(v -> settingsDialog.dismiss());
        // SHOW DIALOG
        settingsDialog.show();
    }

    private void saveToDownloads(Settings s) {
        // INIT MEDIA STORE
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, APP_NAME.toLowerCase() + ".txt");
        cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = getApplicationContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) {
            Log.e(APP_NAME, "Failed to create file URI");
            Snackbar.make(findViewById(android.R.id.content), "Failed to create file", Snackbar.LENGTH_SHORT).show();
            return;
        }
        // SERIALIZE SETTINGS
        StringWriter writer = new StringWriter();
        writer.write(settings.getGridRows() + "\n");
        writer.write(settings.getGridColumns() + "\n");
        writer.write(serializeHomeGrid(settings.getHomeApps()));

        // SAVE TO FILE
        try (OutputStream os = getApplicationContext().getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                Log.e(APP_NAME, "Failed to open output stream");
                Snackbar.make(findViewById(android.R.id.content), "Failed to save file", Snackbar.LENGTH_SHORT).show();
                return;
            }
            os.write(writer.toString().getBytes());
            Snackbar.make(findViewById(android.R.id.content), "Saved to: " + uri, Snackbar.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(APP_NAME, "Failed to write settings to file", e);
            Snackbar.make(findViewById(android.R.id.content), "Failed to export settings", Snackbar.LENGTH_SHORT).show();
        }
    }

    private Settings loadSettingsFile(Uri uri) {
        // INIT MEDIA STORE
        if (Objects.equals(uri.getScheme(), "content")) {
            // LOAD FILE
            try (InputStream is = getApplicationContext().getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                Settings s = new Settings();
                s.setGridRows(Integer.valueOf(reader.readLine()));
                s.setGridColumns(Integer.valueOf(reader.readLine()));
                s.setHomeApps(deserializeHomeGrid(reader.readLine()));
                return s;
            } catch (IOException e) {
                Log.e(APP_NAME, "Failed to import settings", e);
                Snackbar.make(
                        findViewById(android.R.id.content),
                        "Failed to import settings",
                        Snackbar.LENGTH_SHORT
                ).show();
            }
        }
        return null;
    }

    // GET THE DISPLAY HEIGHT (TO CALCULATE GRID CELL HEIGHT)
    private int getDisplayContentHeight() {
        final WindowManager windowManager = getWindowManager();
        final Point size = new Point();
        int screenHeight;
        int actionBarHeight = 0;
        if (getActionBar() != null) {
            actionBarHeight = getActionBar().getHeight();
        }
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimension", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        int contentTop = (findViewById(android.R.id.content)).getTop();
        windowManager.getDefaultDisplay().getSize(size);
        screenHeight = size.y;
        return screenHeight - contentTop - actionBarHeight - statusBarHeight;
    }

    // GENERATE PLACEHOLDER TO FILL POSITIONS WHERE THERE IS NO APP PLACED
    GridItem generatePlaceholder() {
        final GridItem placeHolder = new GridItem();
        placeHolder.setName("");
        placeHolder.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.gridsquare, null));
        return placeHolder;
    }

    // SERIALIZE HOME GRID DATA TO STRING (FOR EXPORT AND/OR PERSIST PURPOSES)
    private String serializeHomeGrid(Map<Integer, GridItem> grid) {
        StringBuilder result = new StringBuilder();
        for (int position : grid.keySet()) {
            String packageName = grid.get(position).getPackageName();
            result
                    .append(position)
                    .append("|")
                    .append(packageName == null ? "" : packageName);
            if ((position + 1) != grid.size()) {
                result.append("#");
            }
        }
        return result.toString();
    }

    // DESERIALIZE HOME GRID DATA FROM STRING (FOR IMPORT AND/OR PERSIST PURPOSES)
    private Map<Integer, GridItem> deserializeHomeGrid(String data) {
        Map<Integer, GridItem> grid = new HashMap<>();
        if (data.isBlank()) {
            return grid;
        }
        for (String dataItem : Objects.requireNonNull(data).split("#")) {
            GridItem app;
            String[] splitDataItem = dataItem.split("\\|");
            int position = Integer.parseInt(splitDataItem[0]);
            if (splitDataItem.length == 2 && !splitDataItem[1].isBlank()) {
                app = new GridItem();
                app.setPosition(position);
                app.setPackageName(splitDataItem[1]);
                for (GridItem a : allAppsGridItems.values()) {
                    if (Objects.equals(a.getPackageName(), app.getPackageName())) {
                        app.setName(a.getName());
                        app.setPackageName(a.getPackageName());
                        app.setIcon(a.getIcon());
                        app.setLaunchIntent(a.getLaunchIntent());
                        break;
                    }
                }
                grid.put(position, app);
            }
        }
        return grid;
    }

    // SAVE SETTINGS TO SHARED PREFERENCES
    private void saveSettings(Settings s) {
        SharedPreferences sp = getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(SP_HOME_APPS_ID, serializeHomeGrid(s.getHomeApps()));
        editor.putInt(SP_GRID_ROWS_ID, s.getGridRows());
        editor.putInt(SP_GRID_COLUMNS_ID, s.getGridColumns());
        editor.apply();
        settings = s;
    }

    // LOAD SETTINGS FROM SHARED PREFERENCES. IF SOME REQUIRED POSITION IN HOME GRID IS NOT
    // DEFINED, GENERATE A NEW PLACEHOLDER FOR THAT POSITION. GET ALL NECESSARY METADATA
    // FOR THE GRID ITEM FROM INSTALLED APP LIST
    private Settings loadSettings() {
        SharedPreferences sp = getSharedPreferences(APP_NAME, Context.MODE_PRIVATE);
        int rows = sp.getInt(SP_GRID_ROWS_ID, 13);
        int columns = sp.getInt(SP_GRID_COLUMNS_ID, 7);
        return new Settings(
                rows,
                columns,
                deserializeHomeGrid(sp.getString(SP_HOME_APPS_ID, ""))
        );
    }

    // LOAD ALL INSTALLED APPS TO SHOW IN "ALL APPS" GRID. LOADING ONLY THOSE APPS WHICH HAVE INTENT
    // DEFINED (NO REASON TO SHOW APPS THAT CAN NOT BE EXECUTED)
    private Map<Integer, GridItem> loadInstalledApps() {
        Map<Integer, GridItem> result = new HashMap<>();
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> ais = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        int i = 0;
        for (ApplicationInfo ai : ais) {
            Intent intent = pm.getLaunchIntentForPackage(ai.packageName);
            if (intent != null) {
                GridItem app = new GridItem();
                app.setName(ai.loadLabel(pm).toString());
                app.setPackageName(ai.packageName);
                app.setIcon(ai.loadIcon(pm));
                app.setLaunchIntent(intent);
                result.put(i, app);
                i++;
            }
        }
        return result;
    }
}