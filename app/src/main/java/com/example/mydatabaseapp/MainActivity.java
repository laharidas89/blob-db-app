package com.example.mydatabaseapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static com.example.mydatabaseapp.SampleConstants.CAMERA_BUCKET;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private ArrayList<String> mListOfAllImages = new ArrayList<>();
    private ArrayList<Uri> mListOfAllUris = new ArrayList<>();
    private ArrayList<byte[]> mBlobs = new ArrayList<>();
    private SampleDBSQLiteHelper mDbHelper;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void verifyStoragePermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {

                }
                return;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        verifyStoragePermissions(this);
        mDbHelper = new SampleDBSQLiteHelper(getApplicationContext());
        Log.i(TAG, "DB set up!");

    }

    public class FetchMediaImages extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            String selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
            String[] selectionArgs = new String[]{CAMERA_BUCKET};

            Cursor imageCursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, selection, selectionArgs, null);
            if (imageCursor != null && imageCursor.moveToFirst()) {
                do {
                    if (Build.VERSION.SDK_INT >= 29) {
                      //
                      int columnValue = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                      Log.i(TAG, String.valueOf(columnValue));
                        mListOfAllUris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columnValue));
                    } else {
                        mListOfAllImages.add(imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA)));
                    }
                } while (imageCursor.moveToNext());
            }
            imageCursor.close();
            return true;
        }
    }

    public void onFetchClicked(View view) {
        boolean result = false;
        try {
            result = (boolean) new FetchMediaImages().execute().get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (result) {
            Toast.makeText(this, "fetched images from media store", Toast.LENGTH_LONG).show();
        }
    }

    public class SaveBlob extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            SQLiteDatabase database = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            byte[] blob;
            if (Build.VERSION.SDK_INT >= 29) {
                for (Uri imageUri : mListOfAllUris) {
                    blob = convertUriToBlob(imageUri);
                    values.put(SampleDBContract.Images.COLUMN_NAME, blob);
                    database.insert(SampleDBContract.Images.TABLE_NAME, null, values);
                }
            } else {
                for (String imagePath : mListOfAllImages) {
                    blob = convertImageToBlob(imagePath);
                    values.put(SampleDBContract.Images.COLUMN_NAME, blob);
                    database.insert(SampleDBContract.Images.TABLE_NAME, null, values);
                }
            }
            return true;
        }
    }

    public void onSaveClicked(View view) {
        boolean result = false;
        try {
            result = (boolean) new SaveBlob().execute().get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (result) {
            Toast.makeText(this, "Blobs inserted", Toast.LENGTH_LONG).show();
        }
    }

    public class ReadBlob extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            SQLiteDatabase database = mDbHelper.getReadableDatabase();

            String[] projection = {
                    SampleDBContract.Images._ID,
                    SampleDBContract.Images.COLUMN_NAME,
            };

            Cursor cursor = database.query(
                    SampleDBContract.Images.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    byte[] blob = cursor.getBlob(cursor.getColumnIndex(SampleDBContract.Images.COLUMN_NAME));
                    Log.i(TAG, String.valueOf(blob));
                } while (cursor.moveToNext());
            }
            return null;
        }
    }

    public void onReadClicked(View view) {
        new ReadBlob().execute();
        Toast.makeText(this, "Blobs read!", Toast.LENGTH_LONG).show();
    }

    private byte[] convertImageToBlob(String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        if (bitmap != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
            return stream.toByteArray();
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private byte[] convertUriToBlob(Uri imageUri) {
        try (ParcelFileDescriptor pfd = getApplicationContext().getContentResolver().openFileDescriptor(imageUri, "r")) {
            if (pfd != null) {
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                if (bitmap != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
                    return stream.toByteArray();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
