package de.unima.ar.collector.sensors;

import android.content.ContentValues;
import android.hardware.Sensor;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.unima.ar.collector.SensorDataCollectorService;
import de.unima.ar.collector.TCPClient;
import de.unima.ar.collector.controller.SQLDBController;
import de.unima.ar.collector.database.DatabaseHelper;
import de.unima.ar.collector.extended.Plotter;
import de.unima.ar.collector.shared.Settings;
import de.unima.ar.collector.shared.database.SQLTableName;
import de.unima.ar.collector.shared.util.DeviceID;
import de.unima.ar.collector.util.DBUtils;
import de.unima.ar.collector.util.PlotConfiguration;


/**
 * @author Fabian Kramm, Timo Sztyler, Nancy Kunath
 */
public class GyroscopeSensorCollector extends SensorCollector
{
    private static final int      type       = 4;
    private static final String[] valueNames = new String[]{ "attr_x", "attr_y", "attr_z", "attr_time" };

    private static Map<String, Plotter>        plotters = new HashMap<>();
    private static Map<String, List<String[]>> cache    = new HashMap<>();

    private static int idx = 0;

    private static TCPClient mTcpClient;
    public static String currentJson;

    public GyroscopeSensorCollector(Sensor sensor)
    {
        super(sensor);

        // create new plotter
        List<String> devices = DatabaseHelper.getStringResultSet("SELECT device FROM " + SQLTableName.DEVICES, null);
        for(String device : devices) {
            GyroscopeSensorCollector.createNewPlotter(device);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor s, int i)
    {
    }


    @Override
    public void SensorChanged(float[] values, long time)
    {
        ContentValues newValues = new ContentValues();
        newValues.put(valueNames[0], values[0]);
        newValues.put(valueNames[1], values[1]);
        newValues.put(valueNames[2], values[2]);
        newValues.put(valueNames[3], time);

        String deviceID = DeviceID.get(SensorDataCollectorService.getInstance());
        if(true){
            GyroscopeSensorCollector.writeSensorData(deviceID, newValues);
        }else{
            GyroscopeSensorCollector.writeDBStorage(deviceID, newValues);
        }
        GyroscopeSensorCollector.updateLivePlotter(deviceID, values);
    }


    @Override
    public Plotter getPlotter(String deviceID)
    {
        return plotters.get(deviceID);
    }


    @Override
    public int getType()
    {
        return type;
    }


    public static void createNewPlotter(String deviceID)
    {
        PlotConfiguration levelPlot = new PlotConfiguration();
        levelPlot.plotName = "LevelPlot";
        levelPlot.rangeMin = -15;
        levelPlot.rangeMax = 15;
        levelPlot.rangeName = "speed";
        levelPlot.SeriesName = "Gyroscope";
        levelPlot.domainName = "Axis";
        levelPlot.domainValueNames = Arrays.copyOfRange(valueNames, 0, 3);
        levelPlot.tableName = SQLTableName.GYROSCOPE;
        levelPlot.sensorName = "Gyroscope";


        PlotConfiguration historyPlot = new PlotConfiguration();
        historyPlot.plotName = "HistoryPlot";
        historyPlot.rangeMin = -15;
        historyPlot.rangeMax = 15;
        historyPlot.domainMin = 0;
        historyPlot.domainMax = 50;
        historyPlot.rangeName = "speed";
        historyPlot.SeriesName = "Gyroscope";
        historyPlot.domainName = "Axis";
        historyPlot.seriesValueNames = Arrays.copyOfRange(valueNames, 0, 3);

        Plotter plotter = new Plotter(deviceID, levelPlot, historyPlot);
        plotters.put(deviceID, plotter);
    }


    public static void updateLivePlotter(String deviceID, float[] values)
    {
        Plotter plotter = plotters.get(deviceID);
        if(plotter == null) {
            GyroscopeSensorCollector.createNewPlotter(deviceID);
            plotter = plotters.get(deviceID);
        }

        plotter.setDynamicPlotData(values);
    }


    public static void createDBStorage(String deviceID)
    {
        String sqlTable = "CREATE TABLE IF NOT EXISTS " + SQLTableName.PREFIX + deviceID + SQLTableName.GYROSCOPE + " (id INTEGER PRIMARY KEY, " + valueNames[3] + " INTEGER, " + valueNames[0] + " REAL, " + valueNames[1] + " REAL, " + valueNames[2] + " REAL)";
        SQLDBController.getInstance().execSQL(sqlTable);
    }

    public static void writeSensorData(String deviceID, ContentValues newValues) {

        //if (Settings.DATABASE_DIRECT_INSERT) {
        if(false){
            if (mTcpClient != null && mTcpClient.getMRun() != false) {
                JSONObject ObJson = new JSONObject();
                try {
                    ObJson.put("deviceID", deviceID);
                    ObJson.put("sensorType", "gyroscope");
                    JSONArray array = new JSONArray();
                    JSONObject values = new JSONObject();
                    values.put("timeStamp", newValues.getAsString("attr_time"));
                    values.put("x", newValues.getAsString("attr_x"));
                    values.put("y", newValues.getAsString("attr_y"));
                    values.put("z", newValues.getAsString("attr_z"));
                    array.put(values);
                    ObJson.put("data", array);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                currentJson = ObJson.toString();
                new GyroscopeSensorCollector.SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            return;
        } else {
            List<String[]> clone = DBUtils.manageCache(deviceID, cache, newValues, 100);
            if (clone != null) {
                JSONObject ObJson = new JSONObject();
                try {
                    ObJson.put("deviceID", deviceID);
                    ObJson.put("sensorType", "gyroscope");
                    JSONArray array = new JSONArray();
                    for (int i = 1; i < clone.size(); i++) {
                        JSONObject values = new JSONObject();
                        values.put("timeStamp", clone.get(i)[1].toString());
                        values.put("x", clone.get(i)[3].toString());
                        values.put("y", clone.get(i)[2].toString());
                        values.put("z", clone.get(i)[0].toString());
                        array.put(values);
                    }
                    ObJson.put("data", array);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (mTcpClient != null && mTcpClient.getMRun() != false) {
                    currentJson = ObJson.toString();
                    new GyroscopeSensorCollector.SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    public static void writeDBStorage(String deviceID, ContentValues newValues)
    {

        if (idx % 8 == 0)  {
            final String tableName = SQLTableName.PREFIX + deviceID + SQLTableName.GYROSCOPE;

            if(Settings.DATABASE_DIRECT_INSERT) {
                SQLDBController.getInstance().insert(tableName, null, newValues);
                return;
            }

            final List<String[]> clone = DBUtils.manageCache(deviceID, cache, newValues, (Settings.DATABASE_CACHE_SIZE + type * 200));

            new Thread(new Runnable() {
                public void run() {
                    if(clone != null) {
                        SQLDBController.getInstance().bulkInsert(tableName, clone);
                    }
                }
            }).start();
        }
        idx++;
    }


    public static void flushDBCache(String deviceID)
    {
        DBUtils.flushCache(SQLTableName.GYROSCOPE, cache, deviceID);
    }

    public void clearCache(String id) {
        cache.remove(id);
    }

    public static void openSocket(String deviceID){
        // connect to the server
        GyroscopeSensorCollector.ConnectTask task = new GyroscopeSensorCollector.ConnectTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void closeSocket(String deviceID){
        // disconnect to the server
        mTcpClient.stopClient();
        //mTcpClient.deregister();
    }

    private static class ConnectTask extends AsyncTask<String,String,TCPClient> {
        public ConnectTask(){
            super();
        }

        @Override
        protected TCPClient doInBackground(String... message) {

            mTcpClient = new TCPClient();
            mTcpClient.run();

            //mTcpClient = TCPClient.getInstance();
            //mTcpClient.register();

            return null;
        }

    }

    private static class SendTask extends AsyncTask<String,String,TCPClient> {
        public SendTask(){
            super();
        }
        @Override
        protected TCPClient doInBackground(String... message) {

            mTcpClient.sendMessage(GyroscopeSensorCollector.currentJson);

            return null;
        }

    }
}