package com.example.mydatabaseapp;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
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

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static com.example.mydatabaseapp.SampleConstants.CAMERA_BUCKET;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private ArrayList<String> mListOfAllImages = new ArrayList<>();
    private ArrayList<Uri> mListOfAllUris = new ArrayList<>();
    private ArrayList<byte[]> mListOfBlobs = new ArrayList<>();
    private ArrayList<Bitmap> mListOfBitmaps = new ArrayList<>();
    private SampleDBSQLiteHelper mDbHelper;
    private boolean mPermGranted;
    private Context mContext;

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
                    Log.i(TAG, "permissions granted");
                    mPermGranted = true;
                } else {
                    Log.i(TAG, "permissions not granted");
                    mPermGranted = false;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();

        verifyStoragePermissions(this);
        mDbHelper = new SampleDBSQLiteHelper(mContext);

    }

    public class FetchMediaImages extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            String selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
            String[] selectionArgs = new String[]{CAMERA_BUCKET};

            Cursor imageCursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, selection, selectionArgs, null);
            if (imageCursor == null || imageCursor.getCount() == 0) {
                Log.i(TAG, "failed to fetch images from media store!");
                Toast.makeText(mContext, "failed to fetch images from media store!", Toast.LENGTH_LONG).show();
                imageCursor.close();
                return false;
            }
            if (imageCursor.moveToFirst()) {
                do {
                    if (Build.VERSION.SDK_INT >= 29) {
                        int columnValue = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
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
        if (!mPermGranted) {
            Toast.makeText(mContext, "please grant necessary permissions to continue!", Toast.LENGTH_LONG).show();
            return;
        } else {
            boolean result = false;
            try {
                result = (boolean) new FetchMediaImages().execute().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (result) {
                Toast.makeText(mContext, "fetched " + mListOfAllUris.size() + " images from media store", Toast.LENGTH_LONG).show();
            }
        }
    }

    public class SaveBlob extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            byte[] blob;
            if (Build.VERSION.SDK_INT >= 29) {
                for (Uri imageUri : mListOfAllUris) {
                    blob = convertUriToBlob(imageUri);
                    if (insertBlobToDB(blob) >= 0) {
                        Log.i(TAG, "inserted blob for " + imageUri);
                        mListOfBlobs.add(blob);
                    } else {
                        Log.i(TAG, "failed to insert blob for " + imageUri);
                    }
                }
            } else {
                for (String imagePath : mListOfAllImages) {
                    blob = convertImageToBlob(imagePath);
                    if (insertBlobToDB(blob) >= 0) {
                        Log.i(TAG, "inserted blob for " + imagePath);
                        mListOfBlobs.add(blob);
                    } else {
                        Log.i(TAG, "failed to insert blob for " + imagePath);
                    }
                }
            }
            return mListOfBlobs.size() > 0;
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

        private long insertBlobToDB(byte[] blob) {
            ContentValues values;
            long rowId = -1;
            if (blob != null) {
                values = new ContentValues();
                values.put(SampleDBContract.Images.COLUMN_NAME, blob);
                rowId = mDbHelper.getWritableDatabase().insert(SampleDBContract.Images.TABLE_NAME, null, values);
            } else {
                Log.i(TAG, "blob is null!");
            }
            return rowId;
        }
    }

    public void onSaveClicked(View view) {
        if (!mPermGranted) {
            Toast.makeText(mContext, "please grant necessary permissions to continue!", Toast.LENGTH_LONG).show();
            return;
        } else {
            boolean result = false;
            try {
                result = (boolean) new SaveBlob().execute().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (result) {
                Toast.makeText(mContext, mListOfBlobs.size() + " Blobs inserted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(mContext, "failed to insert blobs!", Toast.LENGTH_LONG).show();
            }
        }
    }

    public class ReadBlob extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            Log.i("ReadBlob", "started reading blobs");
            SQLiteDatabase readableDatabase = mDbHelper.getReadableDatabase();
            int rowCount = (int) DatabaseUtils.queryNumEntries(readableDatabase, SampleDBContract.Images.TABLE_NAME);
            Log.i("ReadBlob", "number of entries in table : " + rowCount);
            if (rowCount == 0) {
                return false;
            }
            ArrayList<byte[]> wholeBlobDataList = new ArrayList<>();
            String queryLength;
            Cursor cursor;
            int length = 0;
            int from, to;
            int i;
            int chunk_size = (1024 * 1024);
            for (int id = 1; id <= rowCount; id++) {
                Log.i("ReadBlob", "started extracting data for row " + id);
                queryLength = "SELECT length(blob) FROM " + SampleDBContract.Images.TABLE_NAME + " WHERE _id=?";
                cursor = readableDatabase.rawQuery(queryLength, new String[]{String.valueOf(id)});
                if (cursor.moveToFirst()) {
                    length = cursor.getInt(0);
                }
                int numSteps = length / chunk_size + 1;
                Log.i("ReadBlob", "Length of blob is " + length + " Number of Chunks = " + numSteps + " Chunk Size = " + chunk_size);

                i = 0;
                from = 1;
                to = chunk_size;
                while (i < numSteps && length > 0) {
                    if (to > length) to = length;
                    String query = "SELECT substr(blob," + from + "," + (chunk_size) + ") FROM " + SampleDBContract.Images.TABLE_NAME + " WHERE _id=?";
                    Log.i("ReadBlob", "substring query : " + query);
                    cursor.close();
                    cursor = readableDatabase.rawQuery(query, new String[]{String.valueOf(id)});
                    if (cursor.moveToFirst()) {
                        wholeBlobDataList.add(cursor.getBlob(0));
                        Log.i("ReadBlob", "Obtained Blob who's length is " + cursor.getBlob(0).length);
                    }
                    cursor.close();
                    i++;
                    from = (i * chunk_size) + 1;
                    to = from + chunk_size;
                }
                if (!cursor.isClosed()) {
                    cursor.close();
                }
                Log.i("ReadBlob", "finished extracting data for row " + id);
            }
            Log.i("ReadBlob", "finished reading blobs");
            return true;
        }
    }


    public void onReadClicked(View view) {
        if (!mPermGranted) {
            Toast.makeText(mContext, "please grant necessary permissions to continue!", Toast.LENGTH_LONG).show();
            return;
        } else {
            boolean result = false;
            try {
                result = (boolean) new ReadBlob().execute().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (result) {
                Toast.makeText(mContext, "Blobs read!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(mContext, "failed to read blobs!", Toast.LENGTH_LONG).show();
            }
        }
    }

    /*public class getBitmapFromBlob extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            for (byte[] blob : mListOfBlobs) {
                mListOfBitmaps.add(BitmapFactory.decodeByteArray(blob, 0, blob.length));
            }
            return mListOfBitmaps.size();
        }
    }*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDbHelper != null) {
            mDbHelper.close();
            Log.i(TAG, "DB closed!");
        }
    }

    /*public void onGetBitmapClicked(View view) {
        int result = 0;
        try {
            result = (int) new getBitmapFromBlob().execute().get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (result > 0) {
            Toast.makeText(mContext, mListOfBitmaps.size() + " Blobs converted to Bitmap!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, "error while converting blobs to Bitmap!", Toast.LENGTH_LONG).show();
        }
    }*/
}
