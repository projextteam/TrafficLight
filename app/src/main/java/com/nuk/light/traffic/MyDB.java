package com.nuk.light.traffic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public class MyDB extends SQLiteOpenHelper {
    /** Property */
    private boolean mOnCreate;
    private SQLiteDatabase mReadableDB;


    /** Method */
    MyDB(Context context) {
        super(context, "SQLiteDB.db", null, 1);

        mOnCreate = false;
        mReadableDB = getReadableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        /* Create All Table */
        // node
        db.execSQL("CREATE TABLE node (" +
                "Id varchar(15) NOT NULL PRIMARY KEY," +
                "Latitude decimal(10,8) NOT NULL," +
                "Longitude decimal(11,8) NOT NULL," +
                "IsCross tinyint(1) NOT NULL" +
                ");"
        );

        // traffic light
        db.execSQL("CREATE TABLE trafficlight (" +
                "Id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Road1 varchar(10) NOT NULL," +
                "Road2 varchar(10) NOT NULL," +
                "Latitude decimal(10,8) NOT NULL," +
                "Longitude decimal(11,8) NOT NULL" +
                ");"
        );

        // vector
        db.execSQL("CREATE TABLE vector (" +
                "Id varchar(15) NOT NULL PRIMARY KEY," +
                "Name varchar(10) NOT NULL," +
                "Oneway tinyint(1) NOT NULL," +
                "MaxSpeed int(11) DEFAULT NULL" +
                ");"
        );

        // lights
        db.execSQL("CREATE TABLE lights (" +
                "V_id varchar(15) NOT NULL," +
                "L_id int(11) NOT NULL," +
                "Direction int(11) NOT NULL," +
                "PRIMARY KEY(V_id, L_id)," +
                "FOREIGN KEY(V_ID) REFERENCES vector(Id)," +
                "FOREIGN KEY(L_ID) REFERENCES trafficlight(Id)" +
                ");"
        );

        // nodes
        db.execSQL("CREATE TABLE nodes (" +
                "V_id varchar(15) NOT NULL," +
                "N_id varchar(15) NOT NULL," +
                "Number int(11) NOT NULL," +
                "PRIMARY KEY(V_id, N_id)," +
                "FOREIGN KEY(V_ID) REFERENCES vector(Id)," +
                "FOREIGN KEY(N_ID) REFERENCES node(Id)" +
                ");"
        );

        // period
        db.execSQL("CREATE TABLE period (" +
                "Id int(11) NOT NULL," +
                "Red time NOT NULL," +
                "Yellow time NOT NULL," +
                "Green time NOT NULL," +
                "Flash_yellow time NOT NULL," +
                "First_light varchar(20) NOT NULL," +
                "Base_time time NOT NULL," +
                "PRIMARY KEY(Id, Base_time)," +
                "FOREIGN KEY(Id) REFERENCES trafficlight(Id)" +
                ")"
        );

        Log.d("123", "create");
        //event
        db.execSQL("CREATE TABLE event (" +
                "category tinyint(3) not null ," +
                " latitude decimal(11,8) not null ," +
                " longitude decimal(11,8) not null ," +
                " ip varchar(20) not null ," +
                " starttime datetime not null ," +
                " endtime datetime not null ," +
                " status tinyint(1) not null ," +
                " content mediumtext  ," +
                " primary key (category , latitude , longitude) " +
                ")"
        );

        mOnCreate = true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public boolean isOnCreate() {
        return mOnCreate;
    }

    /* 解析 JSON，將所有資料插入資料庫 */
    public void insertAllData(String response) {
        mOnCreate = false;

        SQLiteDatabase db = getWritableDatabase();
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray jsonArray;

            /* 開始事務操作 */
            db.beginTransaction();

            /* Android SQLite 外鍵默認關閉，所以插入各表資料的順序不重要 */
            Iterator<String> tables = jsonObject.keys();
            while (tables.hasNext()) {
                String table = tables.next();
                jsonArray = jsonObject.getJSONArray(table);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject row = jsonArray.getJSONObject(i);
                    ContentValues cv = new ContentValues();

                    Iterator<String> keys = row.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (key.equals("Direction") || key.equals("IsCross") || key.equals("Number")
                                || key.equals("Oneway") || key.equals("MaxSpeed") || key.equals("L_id")
                                || (key.equals("Id") && (table.equals("trafficlight") || table.equals("period")))) {
                            if (!row.isNull(key)) {
                                cv.put(key, row.getInt(key));
                            }
                        } else if (key.equals("Latitude") || key.equals("Longitude")) {
                            cv.put(key, row.getDouble(key));
                        } else {
                            cv.put(key, row.getString(key));
                        }
                    }

                    db.insert(table, null, cv);
                }
            }

            /* 成功 */
            db.setTransactionSuccessful();
            mReadableDB = getReadableDatabase();
        } catch (Exception e) {
            Log.e("MyDB", Log.getStackTraceString(e));
        } finally {
            /* 關閉事務 */
            db.endTransaction();
        }
    }

    public String insertEvent(String response_Event)
    {
        //先執行清空資料表，再重新新增
        mOnCreate = false;
        String key_Event="0";
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM event");

        try {
            JSONArray jsonArray = new JSONArray(response_Event);

            /* 開始事務操作 */
            db.beginTransaction();

            key_Event = jsonArray.get(0).toString();
            for (int i = 1; i < jsonArray.length(); i++)
            {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                ContentValues cv = new ContentValues();
                Iterator<String> keys = jsonObject.keys();

                while(keys.hasNext())
                {
                    String key = keys.next();
                    cv.put(key , jsonObject.get(key).toString());
                }
                db.insert("event", null, cv);
            }
            /* 成功 */
            db.setTransactionSuccessful();
            mReadableDB = getReadableDatabase();
        } catch (Exception e) {
            Log.e("MyDB", Log.getStackTraceString(e));
        } finally {
            /* 關閉事務 */
            db.endTransaction();
        }
        //TODO: 需要將key從MyDB 傳到 MyService  利用bundle? 直接新增　update_key function?
        return  key_Event;
    }

    /* 根據所在的道路名稱找出最靠近的 Node 來判斷位置 */
    public Cursor getNodesOnRoad(String streetName) {
        return mReadableDB.rawQuery("SELECT DISTINCT node.* " +
                "FROM node, nodes, vector " +
                "WHERE node.Id = nodes.N_id AND vector.Id = nodes.V_id AND vector.Name = ?",
                new String[]{streetName});
    }

    /* 取得該路口 Node 在 Node Cursor 所屬之 Vector 的 Number  */
    public int getCrossNumberOnNodeVector(Cursor crossCursor, Cursor nodeCursor) {
        Cursor cursor = mReadableDB.rawQuery("SELECT nodes.Number " +
                "FROM nodes " +
                "WHERE N_id = ? AND V_id = ?",
                new String[]{crossCursor.getString(crossCursor.getColumnIndex("Id")),
                        nodeCursor.getString(nodeCursor.getColumnIndex("V_id"))});
        cursor.moveToFirst();

        int number = cursor.getInt(cursor.getColumnIndex("Number"));
        cursor.close();

        return number;
    }

    /* 取得 Node Cursor 所屬之 Vector 上的其他 Nodes */
    public Cursor getNodesOnNodeVector(Cursor nodeCursor) {
        return mReadableDB.rawQuery("SELECT node.*, nodes.Number " +
                        "FROM node, nodes, vector " +
                        "WHERE node.Id = nodes.N_id AND vector.Id = nodes.V_id AND vector.Id = ?",
                new String[]{nodeCursor.getString(nodeCursor.getColumnIndex("V_id"))});
    }

    /* 取得 Node Cursor 所屬之 Vector 上的紅綠燈 */
    public Cursor getTrafficLightsOnNodeVector(Cursor nodeCursor) {
        return mReadableDB.rawQuery("SELECT trafficlight.*, lights.Direction " +
                        "FROM vector, lights, trafficlight " +
                        "WHERE vector.Id = lights.V_id AND trafficlight.Id = lights.L_id AND lights.V_id = ? " +
                        "ORDER BY lights.Direction",
                new String[]{nodeCursor.getString(nodeCursor.getColumnIndex("V_id"))});
    }

    /* 取得 Node Cursor 相鄰的 Nodes */
    public Cursor getNeighborNodes(Cursor nodeCursor) {
        return mReadableDB.rawQuery("SELECT node.*, nodes.* " +
                        "FROM node, nodes, (SELECT nodes.V_id, nodes.Number FROM vector, nodes WHERE vector.Id = nodes.V_id AND N_id = ?) as x " +
                        "WHERE node.Id = nodes.N_id AND nodes.V_id = x.V_id " +
                        "AND ((nodes.V_id = x.V_id AND nodes.Number = x.Number + 1) OR (nodes.V_id = x.V_id AND nodes.Number = x.Number - 1))" +
                        "ORDER BY nodes.Number",
                new String[]{nodeCursor.getString(nodeCursor.getColumnIndex("Id"))});
    }

    /* 取得紅綠燈 Id 之所有週期 */
    public Cursor getPeriodCursor(int id) {
        return mReadableDB.rawQuery("SELECT * " +
                        "FROM period " +
                        "WHERE Id = " + id + " " +
                        "ORDER BY Base_time",
                new String[]{});
    }

    /* 取得所有事件*/
    public Cursor getAllEvent()
    {
        return  mReadableDB.rawQuery( "SELECT *FROM event WHERE status = 1",null);
    }

    /* 取得特定種類的事件 */
    public Cursor getCategoryEvent (int category_number)
    {
        return  mReadableDB.rawQuery( "SELECT * FROM event WHERE category =" + Integer.toString(category_number) ,null);
    }

    /* 取得所有紅綠燈 */
    public Cursor getAllLights() {
        return mReadableDB.rawQuery("SELECT * FROM trafficlight", null);
    }
}
