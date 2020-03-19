package com.example.mydatabaseapp;

import android.provider.BaseColumns;

public final class SampleDBContract {
    private static final String TAG = SampleDBContract.class.getSimpleName();
    private SampleDBContract() {

    }

    public static class Images implements BaseColumns {
        public static final String TABLE_NAME = "images";
        public static final String COLUMN_NAME = "blob";

        public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " +
                TABLE_NAME + " (" +
                _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " BLOB" + ")";
    }
}
