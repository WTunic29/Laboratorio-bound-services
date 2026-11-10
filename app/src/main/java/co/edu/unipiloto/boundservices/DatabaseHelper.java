package co.edu.unipiloto.boundservices;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "odometer.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_RECORRIDOS = "recorridos";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FECHA = "fecha";
    public static final String COLUMN_LATITUD = "latitud";
    public static final String COLUMN_LONGITUD = "longitud";
    public static final String COLUMN_DISTANCIA = "distancia";
    public static final String COLUMN_DURACION = "duracion";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        String crearTabla = "CREATE TABLE " + TABLE_RECORRIDOS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FECHA + " TEXT, " +
                COLUMN_LATITUD + " REAL, " +
                COLUMN_LONGITUD + " REAL, " +
                COLUMN_DISTANCIA + " REAL, " +
                COLUMN_DURACION + " INTEGER)";

        db.execSQL(crearTabla);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORRIDOS);
        onCreate(db);

    }

    public long guardarRecorrido(String fecha, double latitud, double longitud, double distancia, long duracion) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues valores = new ContentValues();

        valores.put(COLUMN_FECHA, fecha);
        valores.put(COLUMN_LATITUD, latitud);
        valores.put(COLUMN_LONGITUD, longitud);
        valores.put(COLUMN_DISTANCIA, distancia);
        valores.put(COLUMN_DURACION, duracion);

        return db.insert(TABLE_RECORRIDOS, null, valores);

    }


    public Cursor obtenerRecorridos() {

        SQLiteDatabase db = this.getReadableDatabase();

        return db.query(
                TABLE_RECORRIDOS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_ID + " DESC"
        );

    }


    public double obtenerDistanciaTotal() {

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(" + COLUMN_DISTANCIA + "), 0) FROM "
                        + TABLE_RECORRIDOS,
                null
        );

        double total = 0;

        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }

        cursor.close();
        return total;
    }

    public int obtenerTotalRecorridos() {

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_RECORRIDOS, null);

        int total = 0;

        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }

        cursor.close();
        return total;

    }


    public long obtenerDuracionTotal() {

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COALESCE(SUM(" + COLUMN_DURACION + "), 0) FROM " + TABLE_RECORRIDOS, null);

        long total = 0;

        if (cursor.moveToFirst()) {
            total = cursor.getLong(0);
        }

        cursor.close();
        return total;

    }

}
