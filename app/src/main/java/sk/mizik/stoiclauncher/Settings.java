package sk.mizik.stoiclauncher;

import java.util.Map;

// SIMPLE POJO FOR SETTINGS DATA
public class Settings {
    private Integer gridRows;
    private Integer gridColumns;
    private Map<Integer, GridItem> homeApps;

    public Settings() {

    }

    public Settings(int rows, int columns, Map<Integer, GridItem> grid) {
        this.gridRows = rows;
        this.gridColumns = columns;
        this.homeApps = grid;
    }

    public Integer getGridRows() {
        return gridRows;
    }

    public void setGridRows(Integer gridRows) {
        this.gridRows = gridRows;
    }

    public Integer getGridColumns() {
        return gridColumns;
    }

    public void setGridColumns(Integer gridColumns) {
        this.gridColumns = gridColumns;
    }

    public Map<Integer, GridItem> getHomeApps() {
        return homeApps;
    }

    public void setHomeApps(Map<Integer, GridItem> homeApps) {
        this.homeApps = homeApps;
    }
}
