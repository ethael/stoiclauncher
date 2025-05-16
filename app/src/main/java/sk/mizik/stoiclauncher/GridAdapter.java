package sk.mizik.stoiclauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.Map;

/**
 * BUSINESS LOGIC FOR BOTH HOME GRID AND "ALL APPS" GRID
 */
class GridAdapter extends BaseAdapter {
    // CONTEXT
    Context context;
    // GRID ITEMS TO BE SHOWN
    Map<Integer, GridItem> apps;
    // HEIGHT OF ONE GRID CELL
    int cellHeight;
    // WHETHER THIS ADAPTER IS CONTROLLING THE HOME GRID OR THE "ALL APPS" GRID
    boolean onHome;

    public GridAdapter(Context context, Map<Integer, GridItem> apps, int cellHeight, boolean onHome) {
        this.context = context;
        this.apps = apps;
        this.cellHeight = cellHeight;
        this.onHome = onHome;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View recycledView, ViewGroup parent) {
        // RENDER VIEW FOR SINGLE GRID ITEM
        View v;
        if (recycledView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.grid_item, parent, false);
        } else {
            v = recycledView;
        }
        // SET CORRECT APP ICON
        ImageView gridItemImage = v.findViewById(R.id.grid_item_image);
        gridItemImage.setImageDrawable(apps.get(position).getIcon());

        if (recycledView == null) {
            LinearLayout gridItemLayout = v.findViewById(R.id.grid_item_layout);
            // SET CORRECT CELL HEIGHT
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight);
            gridItemLayout.setLayoutParams(params);
            // ADD ON CLICK LISTENER
            gridItemLayout.setOnClickListener(v1 -> ((MainActivity) context).onGridItemPressed(apps.get(position), position, onHome));
            // ADD ON LONG PRESS LISTENER
            gridItemLayout.setOnLongClickListener(v2 -> {
                ((MainActivity) context).onGridItemLongPressed(apps.get(position), onHome, v);
                return true;
            });
        }
        return v;
    }
}