package com.example.zooapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "zooapp.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_USERS = "usuarios";
    public static final String TABLE_MEASUREMENTS = "mediciones";
    public static final String TABLE_HISTORY = "historial_cambios";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tabla de usuarios para autenticación
        db.execSQL("CREATE TABLE " + TABLE_USERS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT)");

        // Estado actual del hábitat por animal (Clave Única por animal)
        db.execSQL("CREATE TABLE " + TABLE_MEASUREMENTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "animal TEXT UNIQUE, " +
                "habitat TEXT, " +
                "temperatura REAL, " +
                "humedad REAL, " +
                "co2 INTEGER, " +
                "fecha TEXT)");

        // Historial cronológico de modificaciones
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "animal TEXT, " +
                "temperatura REAL, " +
                "humedad REAL, " +
                "co2 INTEGER, " +
                "origen TEXT, " +
                "fecha TEXT)");

        // Usuario por defecto cifrado SHA-256 (ISO 27400)
        ContentValues cv = new ContentValues();
        cv.put("username", "admin");
        cv.put("password", hashPassword("admin123"));
        db.insert(TABLE_USERS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return password;
        }
    }

    public boolean validateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE username=? AND password=?",
                new String[]{username, hashPassword(password)});
        boolean valid = cursor.getCount() > 0;
        cursor.close();
        return valid;
    }

    public void saveMeasurementAndHistory(String animal, String habitat, double temp, double hum, int co2, String date, String origen) {
        SQLiteDatabase db = this.getWritableDatabase();

        // 1. Reemplazar o insertar atómicamente el registro actual del animal
        ContentValues cvMeas = new ContentValues();
        cvMeas.put("animal", animal);
        cvMeas.put("habitat", habitat);
        cvMeas.put("temperatura", temp);
        cvMeas.put("humedad", hum);
        cvMeas.put("co2", co2);
        cvMeas.put("fecha", date);

        db.insertWithOnConflict(TABLE_MEASUREMENTS, null, cvMeas, SQLiteDatabase.CONFLICT_REPLACE);

        // 2. Registrar la entrada en la tabla de historial
        ContentValues cvHist = new ContentValues();
        cvHist.put("animal", animal);
        cvHist.put("temperatura", temp);
        cvHist.put("humedad", hum);
        cvHist.put("co2", co2);
        cvHist.put("origen", origen);
        cvHist.put("fecha", date);
        db.insert(TABLE_HISTORY, null, cvHist);
    }

    public Cursor getCurrentMeasurementByAnimal(String animal) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEASUREMENTS + " WHERE animal=?", new String[]{animal});
    }

    public Cursor getHistoryByAnimal(String animal) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_HISTORY + " WHERE animal=? ORDER BY id DESC", new String[]{animal});
    }

    public Cursor getAllCurrentMeasurements() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEASUREMENTS + " ORDER BY id DESC", null);
    }
}