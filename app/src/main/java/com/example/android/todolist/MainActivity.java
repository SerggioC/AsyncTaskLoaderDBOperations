/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.android.todolist;

import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.os.OperationCanceledException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.android.todolist.data.TaskContract;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static com.example.android.todolist.AddTaskActivity.ADDED_NEW_TASK_BOOLEAN;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    // Constants for logging and referring to a unique loader
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int TASK_LOADER_ID = 0;
    private static final int DELETE_LISTENER_ID = 1;
    private static final int QUERY_LOADER_TOKEN = 10;
    private static final int UPDATE_LOADER_TOKEN = 11;
    RecyclerView mRecyclerView;
    int requestCode_id = 1;
    private CustomCursorAdapter mAdapter;
    private Cursor mCursor;
    private Context mContext;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = findViewById(R.id.recyclerViewTasks);

        // Set the layout for the RecyclerView to be a linear layout, which measures and
        // positions items within a RecyclerView into a linear list
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize the adapter and attach it to the RecyclerView
        mAdapter = new CustomCursorAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mContext = getBaseContext();

        /*
         Add a touch helper to the RecyclerView to recognize when a user swipes to delete an item.
         An ItemTouchHelper enables touch behavior (like swipe and move) on each ViewHolder,
         and uses callbacks to signal when a user is performing these actions.
         */
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            // Called when a user swipes left or right on a ViewHolder
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {

                // COMPLETED (1) Construct the URI for the item to delete
                //[Hint] Use getTag (from the adapter code) to get the id of the swiped item
                // Retrieve the id of the task to delete
                int id = (int) viewHolder.itemView.getTag();

                // Build appropriate uri with String row id appended
                String stringId = Integer.toString(id);
                Uri uri = TaskContract.TaskEntry.CONTENT_URI.buildUpon().appendPath(stringId).build();

                AsyncTaskLoader<Integer> deleteAsyncTaskLoader = new DeleteLoader(mContext, uri);
//                AsyncTaskLoader<Integer> deleteAsyncTaskLoader = new AsyncTaskLoader<Integer>(mContext) {
//
//                    @Override
//                    public Integer loadInBackground() {
//                        Integer deleteResult = getContentResolver().delete(uri, null, null);
//                        super.deliverResult(deleteResult);
//                        return deleteResult;
//                    }
//
//                    @Override
//                    public void deliverResult(Integer data) {
//                        super.deliverResult(data);
//                    }
//                };
                Loader.OnLoadCompleteListener loadCompleteListener = new Loader.OnLoadCompleteListener() {
                    /**
                     * Called on the thread that created the Loader when the load is complete.
                     *
                     * @param loader the loader that completed the load
                     * @param data   the result of the load
                     */
                    @Override
                    public void onLoadComplete(Loader loader, Object data) {
                        Log.w("Sergio>", this + " onLoadComplete\ndata= " + data);
                        if (data != null) {
                            if ((int) data != 0) {
                                loader.startLoading();
                                getSupportLoaderManager().restartLoader(TASK_LOADER_ID, null, MainActivity.this);
                            } else {
                                // complete condition
                            }
                        } else {
                            // complete condition
                        }
                    }

                };
                deleteAsyncTaskLoader.registerListener(DELETE_LISTENER_ID, loadCompleteListener);
                deleteAsyncTaskLoader.loadInBackground();


                // COMPLETED (3) Restart the loader to re-query for all tasks after a deletion
                //getSupportLoaderManager().restartLoader(TASK_LOADER_ID, null, MainActivity.this);

                //mAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
                //getSupportLoaderManager().restartLoader(TASK_LOADER_ID, null, MainActivity.this);


//                mAdapter.notifyItemRangeChanged(viewHolder.getAdapterPosition(), mAdapter.getItemCount());
//                mAdapter.notifyDataSetChanged();


            }
        }).attachToRecyclerView(mRecyclerView);

        /*
         Set the Floating Action Button (FAB) to its corresponding View.
         Attach an OnClickListener to it, so that when it's clicked, a new intent will be created
         to launch the AddTaskActivity.
         */
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Create a new intent to start an AddTaskActivity
                Intent addTaskIntent = new Intent(MainActivity.this, AddTaskActivity.class);
                startActivityForResult(addTaskIntent, requestCode_id);
            }
        });

        FloatingActionButton insert = findViewById(R.id.insert);
        insert.setImageBitmap(getBitmapFromText("Query", 40, Color.GREEN));
        insert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MyAsyncQueryHandler myAsyncQueryHandler = new MyAsyncQueryHandler(getContentResolver());
                myAsyncQueryHandler.startQuery(QUERY_LOADER_TOKEN,
                        null,
                        TaskContract.TaskEntry.CONTENT_URI,
                        null,
                        null,
                        null,
                        TaskContract.TaskEntry.COLUMN_PRIORITY);
            }
        });

        FloatingActionButton update = findViewById(R.id.update);
        update.setImageBitmap(getBitmapFromText("Update", 40, Color.GREEN));
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MyAsyncQueryHandler myAsyncQueryHandler = new MyAsyncQueryHandler(getContentResolver());
                ContentValues contentvalues = new ContentValues(1);
                contentvalues.put(TaskContract.TaskEntry.COLUMN_PRIORITY, "14");
                myAsyncQueryHandler.startUpdate(UPDATE_LOADER_TOKEN,
                        null,
                        TaskContract.TaskEntry.CONTENT_URI.buildUpon().appendPath("14").build(),
                        contentvalues,
                        TaskContract.TaskEntry._ID,
                        new String[]{"1"});

            }
        });


        /*
         Ensure a loader is initialized and active. If the loader doesn't already exist, one is
         created, otherwise the last created loader is re-used.
         */
        //Log.i("Sergio>", this + " onCreate initLoader");
        getSupportLoaderManager().initLoader(TASK_LOADER_ID, null, this);





    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == requestCode_id && resultCode == RESULT_OK) {
            Boolean added_new = data.getBooleanExtra(ADDED_NEW_TASK_BOOLEAN, false);
            if (added_new) {
                getSupportLoaderManager().restartLoader(TASK_LOADER_ID, null, this);
            }
        }

    }

    /**
     * This method is called after this activity has been paused or restarted.
     * Often, this is after new data has been inserted through an AddTaskActivity,
     * so this restarts the loader to re-query the underlying data for any changes.
     */
    @Override
    protected void onResume() {
        TextView textView = new TextView(this);
        textView.setError("wrre");
        super.onResume();


//            // re-queries for all tasks
//            Log.d("Sergio>", this + " onResume restartloader");
        //getSupportLoaderManager().restartLoader(TASK_LOADER_ID, null, this);

    }

    /**
     * Instantiates and returns a new AsyncTaskLoader with the given ID.
     * This loader will return task data as a Cursor or null if an error occurs.
     * <p>
     * Implements the required callbacks to take care of loading data at all stages of loading.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, final Bundle loaderArgs) {

        return new AsyncTaskLoader<Cursor>(this) {

            // Initialize a Cursor, this will hold all the task data
            Cursor mTaskData = null;


            // onStartLoading() is called when a loader first starts loading data
            @Override
            protected void onStartLoading() {
                if (mTaskData != null) {
                    // Delivers any previously loaded data immediately
                    deliverResult(mTaskData);
                    Log.i("Sergio>", this + " onStartLoading mTaskData != null, after deliverResult");

                } else {
                    // Force a new load
                    forceLoad();
                    Log.i("Sergio>", this + " onStartLoading mTaskData == null, after forceLoad");

                }
            }

            // loadInBackground() performs asynchronous loading of data
            @Override
            public Cursor loadInBackground() {
                Log.i("Sergio>", this + " loadInBackground");

                // Will implement to load data

                // Query and load all task data in the background; sort by priority
                // [Hint] use a try/catch block to catch any errors in loading data

                try {
                    return getContentResolver().query(TaskContract.TaskEntry.CONTENT_URI,
                            null,
                            null,
                            null,
                            TaskContract.TaskEntry.COLUMN_PRIORITY);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to asynchronously load data.");
                    e.printStackTrace();
                    return null;
                }
            }

            // deliverResult sends the result of the load, a Cursor, to the registered listener
            public void deliverResult(Cursor data) {
                Log.i("Sergio>", this + " deliverResult before super");
                mTaskData = data;
                super.deliverResult(data);
                Log.i("Sergio>", this + " deliverResult after super");

            }
        };

    }

    /**
     * Called when a previously created loader has finished its load.
     *
     * @param loader The Loader that has finished.
     * @param data   The data generated by the Loader.
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.i("Sergio>", this + " onLoadFinished");

        // Update the data that the adapter uses to create ViewHolders
        mAdapter.swapCursor(data);
    }

    /**
     * Called when a previously created loader is being reset, and thus
     * making its data unavailable.
     * onLoaderReset removes any references this activity had to the loader's data.
     *
     * @param loader The Loader that is being reset.
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.i("Sergio>", this + " onLoaderReset");
        mAdapter.swapCursor(null);
    }

    static class QueryLoader extends AsyncTaskLoader<Cursor> {
        WeakReference weakContext;
        Uri queryUri;
        String[] projection;
        String selection;
        String[] selectionArgs;
        String sortOrder;

        public QueryLoader(@NonNull Context context, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.queryUri = uri;
            this.projection = projection;
            this.selection = selection;
            this.selectionArgs = selectionArgs;
            this.sortOrder = sortOrder;
        }

        @Nullable
        @Override
        public Cursor loadInBackground() {
            Cursor cursor = ((Context) weakContext.get()).getContentResolver().query(queryUri, projection, selection, selectionArgs, sortOrder);
            super.deliverResult(cursor);
            return cursor;
        }

    }

    static class DeleteLoader extends AsyncTaskLoader<Integer> {
        WeakReference weakContext;
        Uri deleteUri;

        public DeleteLoader(@NonNull Context context, Uri uri) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.deleteUri = uri;
        }

        /**
         * Called on a worker thread to perform the actual load and to return
         * the result of the load operation.
         * <p>
         * Implementations should not deliver the result directly, but should return them
         * from this method, which will eventually end up calling {@link #deliverResult} on
         * the UI thread.  If implementations need to process the results on the UI thread
         * they may override {@link #deliverResult} and do so there.
         * <
         * To support cancellation, this method should periodically check the value of
         * {@link #isLoadInBackgroundCanceled} and terminate when it returns true.
         * Subclasses may also override {@link #cancelLoadInBackground} to interrupt the load
         * directly instead of polling {@link #isLoadInBackgroundCanceled}.
         * <
         * When the load is canceled, this method may either return normally or throw
         * {@link OperationCanceledException}.  In either case, the {@link Loader} will
         * call {@link #onCanceled} to perform post-cancellation cleanup and to dispose of the
         * result object, if any.
         *
         * @return The result of the load operation.
         * @throws OperationCanceledException if the load is canceled during execution.
         * @see #isLoadInBackgroundCanceled
         * @see #cancelLoadInBackground
         * @see #onCanceled
         */
        @Nullable
        @Override
        public Integer loadInBackground() {
            Integer deleteResult = ((Context) weakContext.get()).getContentResolver().delete(deleteUri, null, null);
            super.deliverResult(deleteResult);
            return deleteResult;
        }

    }

    static class InsertLoader extends AsyncTaskLoader<Uri> {
        WeakReference weakContext;
        Uri insertUri;
        ContentValues contentValues;

        public InsertLoader(@NonNull Context context, Uri uri, ContentValues contentValues) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.insertUri = uri;
            this.contentValues = contentValues;
        }

        @Nullable
        @Override
        public Uri loadInBackground() {
            Uri returnUri = ((Context) weakContext.get()).getContentResolver().insert(insertUri, contentValues);
            super.deliverResult(returnUri);
            return returnUri;
        }

    }

    static class UpdateLoader extends AsyncTaskLoader<Integer> {
        WeakReference weakContext;
        Uri updateUri;
        ContentValues contentValues;
        String where;
        String[] selectionArgs;

        public UpdateLoader(@NonNull Context context, Uri uri, ContentValues contentValues, String where, String[] selectionArgs) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.updateUri = uri;
            this.contentValues = contentValues;
            this.where = where;
            this.selectionArgs = selectionArgs;
        }

        @Nullable
        @Override
        public Integer loadInBackground() {
            int returnUri = ((Context) weakContext.get()).getContentResolver().update(updateUri, contentValues, where, selectionArgs);
            super.deliverResult(returnUri);
            return returnUri;
        }

    }

    static class ApplyBatchLoader extends AsyncTaskLoader<ContentProviderResult[]> {
        WeakReference weakContext;
        Uri applyBatchUri;
        ContentValues[] contentValuesArray;

        public ApplyBatchLoader(@NonNull Context context, Uri uri, ContentValues[] contentValuesArray) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.applyBatchUri = uri;
            this.contentValuesArray = contentValuesArray;
        }

        @Nullable
        @Override
        public ContentProviderResult[] loadInBackground() {
            ArrayList<ContentProviderOperation> operations = prepareOperations();

            ContentProviderResult[] result = null;
            try {
                result = ((Context) weakContext.get()).getContentResolver().applyBatch(applyBatchUri.getAuthority(), operations);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (OperationApplicationException e) {
                e.printStackTrace();
            }
            super.deliverResult(result);
            return result;
        }

        ArrayList<ContentProviderOperation> prepareOperations() {
            int contentValueslength = contentValuesArray.length;
            ArrayList<ContentProviderOperation> operationsExample = new ArrayList<>(contentValueslength);

            //// Example batch of operations
            //// One insert one update and one delete
            //// Might want to receive the prepared operations in the Loader instead of ContentValuesArray
            operationsExample.add(ContentProviderOperation.newInsert(applyBatchUri)
                    .withValues(contentValuesArray[0])
                    .withYieldAllowed(true)
                    .build());

            operationsExample.add(ContentProviderOperation.newUpdate(applyBatchUri)
                    .withValues(contentValuesArray[1])
                    .withYieldAllowed(true)
                    .build());

            operationsExample.add(ContentProviderOperation.newDelete(applyBatchUri)
                    .withValues(contentValuesArray[2])
                    .withYieldAllowed(true)
                    .build());

            return operationsExample;
        }

    }

    static class BulkInsertLoader extends AsyncTaskLoader<Integer> {
        WeakReference weakContext;
        Uri insertUri;
        ContentValues[] contentValuesArray;

        public BulkInsertLoader(@NonNull Context context, Uri uri, ContentValues[] contentValuesArray) {
            super(context);
            this.weakContext = new WeakReference(context);
            this.insertUri = uri;
            this.contentValuesArray = contentValuesArray;
        }

        @Nullable
        @Override
        public Integer loadInBackground() {
            Integer insertedValues = ((Context) weakContext.get()).getContentResolver().bulkInsert(insertUri, contentValuesArray);

//            ContentProviderResult[] providerResult = null;
//
//            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
//            for (int i = 0; i < contentValuesArray.length; i++) {
//                operations.add(ContentProviderOperation.newInsert(insertUri)
//                        .withValues(contentValuesArray[i])
//                        .withYieldAllowed(true)
//                        .build());
//            }
//            try {
//                providerResult = getContext().getContentResolver().applyBatch(insertUri.getAuthority(), operations);
//            } catch (RemoteException e) {
//                e.printStackTrace();
//            } catch (OperationApplicationException e) {
//                e.printStackTrace();
//            }

            super.deliverResult(insertedValues);
            return insertedValues;
        }
    }



    private class MyAsyncQueryHandler extends AsyncQueryHandler {
        ContentResolver contentResolver;

        public MyAsyncQueryHandler(ContentResolver cr) {
            super(cr);
            this.contentResolver = cr;
        }

        @Override
        protected Handler createHandler(Looper looper) {
            return super.createHandler(looper);
        }



        /**
         * This method begins an asynchronous query. When the query is done
         * {@link #onQueryComplete} is called.
         *
         * @param token         A token passed into {@link #onQueryComplete} to identify
         *                      the query.
         * @param cookie        An object that gets passed into {@link #onQueryComplete}
         * @param uri           The URI, using the content:// scheme, for the content to
         *                      retrieve.
         * @param projection    A list of which columns to return. Passing null will
         *                      return all columns, which is discouraged to prevent reading data
         *                      from storage that isn't going to be used.
         * @param selection     A filter declaring which rows to return, formatted as an
         *                      SQL WHERE clause (excluding the WHERE itself). Passing null will
         *                      return all rows for the given URI.
         * @param selectionArgs You may include ?s in selection, which will be
         *                      replaced by the values from selectionArgs, in the order that they
         *                      appear in the selection. The values will be bound as Strings.
         * @param orderBy       How to order the rows, formatted as an SQL ORDER BY
         *                      clause (excluding the ORDER BY itself). Passing null will use the
         */
        @Override
        public void startQuery(int token, Object cookie, Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
            super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
            //contentResolver.query(uri, projection, selection, selectionArgs, orderBy);
        }


        /**
         * Called when an asynchronous query is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startQuery}.
         * @param cookie the cookie object passed in from {@link #startQuery}.
         * @param cursor The cursor holding the results from the query.
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            super.onQueryComplete(token, cookie, cursor);
            mCursor = cursor;
            mAdapter.swapCursor(cursor);
        }

        /**
         * Called when an asynchronous insert is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startInsert}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startInsert}.
         * @param uri    the uri returned from the insert operation.
         */
        @Override
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            super.onInsertComplete(token, cookie, uri);

        }

        /**
         * Called when an asynchronous update is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startUpdate}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startUpdate}.
         * @param result the result returned from the update operation
         */
        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            super.onUpdateComplete(token, cookie, result);
        }

        /**
         * Called when an asynchronous delete is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startDelete}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startDelete}.
         * @param result the result returned from the delete operation
         */
        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    public static Bitmap getBitmapFromText(String text, float textSize, int textColor) {
        Paint paint = new Paint(ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -paint.ascent(); // ascent() is negative
        int width = (int) (paint.measureText(text) + 0.0f); // round
        int height = (int) (baseline + paint.descent() + 0.0f);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(text, 0, baseline, paint);
        return bitmap;
    }
}

