/*
  MIT License

  Copyright (c) 2017 Elyasin Shaladi

  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
  associated documentation files (the "Software"), to deal in the Software without restriction,
  including without limitation the rights to use, copy, modify, merge, publish, distribute,
  sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all copies or
  substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
  NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.example.android.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.android.popularmovies.adapters.MovieAdapter;
import com.example.android.popularmovies.asyncTasks.AsyncTaskListener;
import com.example.android.popularmovies.asyncTasks.MoviesLocalQueryTask;
import com.example.android.popularmovies.asyncTasks.MoviesQueryTask;
import com.example.android.popularmovies.data.MovieContract;
import com.example.android.popularmovies.models.Movie;
import com.example.android.popularmovies.utilities.NetworkUtils;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;

/**
 * {@link MainActivity} displaying selectable movie posters in a grid (based on LinearLayout).
 * By default it displays Popular Movies selection.
 */

public class MainActivity extends AppCompatActivity implements
        AdapterView.OnItemSelectedListener,
        MovieAdapter.MovieAdapterOnClickHandler {

    // Projection and indices for movies
    public static final String[] MOVIES_PROJECTION = {
            MovieContract.MovieEntry.COLUMN_MOVIE_ID,
            MovieContract.MovieEntry.COLUMN_MOVIE_POSTER_PATH,
            MovieContract.MovieEntry.COLUMN_MOVIE_W92_POSTER,
            MovieContract.MovieEntry.COLUMN_MOVIE_W185_POSTER,
            MovieContract.MovieEntry.COLUMN_MOVIE_FAVORITE
    };

    public static final int INDEX_MOVIE_ID = 0;
    public static final int INDEX_MOVIE_POSTER_PATH = 1;
    public static final int INDEX_MOVIE_W92_POSTER = 2;
    public static final int INDEX_MOVIE_W185_POSTER = 3;
    public static final int INDEX_MOVIE_FAVORITE = 4;


    //Define three queries: popular, top rated and favorite
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({POPULAR_MOVIES, TOP_RATED_MOVIES, FAVORITE_MOVIES})
    public @interface MOVIES_QUERY {
    }

    //Valuse must be 0, 1, 2 in order because they map to the spinner items in the menu
    public static final int POPULAR_MOVIES = 0;     //position 0 in spinner
    public static final int TOP_RATED_MOVIES = 1;   //position 1 in spinner
    public static final int FAVORITE_MOVIES = 2;    //position 2 in spinner

    private static final String LOG_TAG = MainActivity.class.getSimpleName();


    private MovieAdapter mMovieAdapter;
    private RecyclerView mRecyclerViewMovies;
    private Parcelable mRecyclerViewMoviesState;
    private GridLayoutManager mLayoutManager;

    private ProgressBar mLoadingIndicator;

    private TextView mErrorMessageDisplay;


    private int mMovieQuery;


    /**
     * Sets up {@link MainActivity} and initially queries database.
     *
     * @param savedInstanceState Bundle of Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mErrorMessageDisplay = (TextView) findViewById(R.id.tv_error_message);
        mErrorMessageDisplay.setText(getString(R.string.no_internet_access));

        mLoadingIndicator = (ProgressBar) findViewById(R.id.pb_loading_indicator);

        mRecyclerViewMovies = (RecyclerView) findViewById(R.id.rv_movies);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLayoutManager = new GridLayoutManager(this, 2);
        } else {
            mLayoutManager = new GridLayoutManager(this, 3);
        }
        mRecyclerViewMovies.setHasFixedSize(true);
        mRecyclerViewMovies.setLayoutManager(mLayoutManager);
        mMovieAdapter = new MovieAdapter(this, this);

        //Setting the adapter will execute notifyDataSetChanged, so no need to query twice
        mRecyclerViewMovies.setAdapter(mMovieAdapter);
        //Picasso.with(this).setIndicatorsEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMovieQuery == FAVORITE_MOVIES) {
            Log.d(LOG_TAG, "Querying favorite database afresh");
            queryMovieDatabase(FAVORITE_MOVIES);
        }
    }

    /**
     * Reads the saved position to query for corresponding movie.
     *
     * @param savedInstanceState The saved state contains the movie query.
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mMovieQuery = savedInstanceState.getInt(getString(R.string.movie_query_key), POPULAR_MOVIES);

        //State is saved now, but restored after data is loaded
        // see onTaskComplete methods of AsyncTaskListener
        mRecyclerViewMoviesState = savedInstanceState.getParcelable(getString(R.string.recylcer_view_movies_state_key));

        Log.d(LOG_TAG, "onRestoreInstanceState");
    }

    /**
     * Saves the position (movie query) for the spinner.
     *
     * @param outState Saved state when activity is destroyed.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(getString(R.string.movie_query_key), mMovieQuery);

        mRecyclerViewMoviesState = mRecyclerViewMovies.getLayoutManager().onSaveInstanceState();//save
        outState.putParcelable(getString(R.string.recylcer_view_movies_state_key), mRecyclerViewMoviesState);

        Log.d(LOG_TAG, "onSaveInstanceState");
    }


    /**
     * Depending on the movie query either
     * <p>
     * - Constructs the URL (using {@link NetworkUtils}) and fires off
     * an AsyncTask to perform the GET request using our {@link MoviesQueryTask}.
     * If there is no internet connection a message is displayed.
     * <p>
     * - Fires off an AsyncTask to perform a query on local storage
     * ({@link com.example.android.popularmovies.data.MovieContentProvider}) using
     * {@link MoviesLocalQueryTask}.
     *
     * @param movieQuery The movie query key.
     */
    private void queryMovieDatabase(@MOVIES_QUERY int movieQuery) {

        if (movieQuery == POPULAR_MOVIES || movieQuery == TOP_RATED_MOVIES) {
            if (NetworkUtils.isOnline()) {
                URL url = NetworkUtils.buildMoviesURL(movieQuery);
                new MoviesQueryTask(this, new MoviesQueryTaskListener()).execute(url);
            } else {
                mRecyclerViewMovies.setVisibility(View.INVISIBLE);
                mErrorMessageDisplay.setVisibility(View.VISIBLE);
            }
        } else if (movieQuery == FAVORITE_MOVIES) {
            Uri uri = MovieContract.MovieEntry.CONTENT_URI;
            new MoviesLocalQueryTask(this, new MoviesLocalQueryTaskListener()).execute(uri);
        } else {
            Log.d(LOG_TAG, "Did not query any database.");
        }
    }

    /**
     * Add a spinner with three options (popular, top-rated and favorites) to the menu.
     *
     * @param menu - The menu in which our items are placed.
     * @return true to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main, menu);

        //Find/Create the action bar spinner
        MenuItem item = menu.findItem(R.id.query_spinner);
        Spinner spinner = (Spinner) MenuItemCompat.getActionView(item);

        //Set the spinner adapter
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.spinner_items, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(mMovieQuery);

        //Register MainActivity as listener
        //Should be set after the setAdapter is called, otherwise will trigger with default option
        spinner.setOnItemSelectedListener(this);

        return true;
    }

    /**
     * Queries the movie database for selected item: popular or top-rated or favorites
     * The Favorites are from the local storage
     * ({@link com.example.android.popularmovies.data.MovieContentProvider}).
     * <p>
     * Note: Initial value is fired when activity is started. Annoying, but true.
     *
     * @param adapterView The AdapterView were selection happened.
     * @param view        The view that was clicked (within the AdapterView).
     * @param pos         The position of the selected item.
     * @param id          Row id of selected item.
     */
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {

        if (adapterView.getItemAtPosition(pos).equals(getString(R.string.popular))) {
            this.setTitle(getString(R.string.popular));
            queryMovieDatabase(POPULAR_MOVIES);
            mMovieQuery = POPULAR_MOVIES;
            Log.d(LOG_TAG, "Display popular movies");

        } else if (adapterView.getItemAtPosition(pos).equals(getString(R.string.top_rated))) {
            this.setTitle(getString(R.string.top_rated));
            queryMovieDatabase(TOP_RATED_MOVIES);
            mMovieQuery = TOP_RATED_MOVIES;
            Log.d(LOG_TAG, "Display top-rated movies");

        } else if (adapterView.getItemAtPosition(pos).equals(getString(R.string.favorite))) {
            this.setTitle(getString(R.string.favorite));
            queryMovieDatabase(FAVORITE_MOVIES);
            mMovieQuery = FAVORITE_MOVIES;
            Log.d(LOG_TAG, "Display Favorite movies");
        }


    }

    /**
     * Not implemented.
     *
     * @param adapterView The AdapterView (does not contain selected item).
     */
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.v(LOG_TAG, "Nothing selected");
    }

    /**
     * Shows details of a selected movie in a new Activity.
     *
     * @param movie The movie that was clicked on.
     */
    @Override
    public void onClick(Movie movie, View view) {

        if (movie.getW185Poster() == null) {

            //Save the image bytes in movie in case it is added to favorites
            ImageView imageView = (ImageView) view.findViewById(R.id.iv_w185_poster);
            Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
            ByteArrayOutputStream binOutStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, binOutStream);
            byte[] imageInByte = binOutStream.toByteArray();
            movie.setW185Poster(imageInByte);

            Log.d(LOG_TAG, "W185 poster added to movie. Size is " + binOutStream.size() / 1024 + " KB.");
        }

        //Now start the detail activity
        Context context = MainActivity.this;
        Class destinationActivity = DetailActivity.class;
        Intent startDetailActivity = new Intent(context, destinationActivity);
        startDetailActivity.putExtra(getString(R.string.movie_key), movie);
        startActivity(startDetailActivity);
    }


    /**
     * Listener executed by onPreExecute and onPostExecute functionality of corresponding
     * AsyncTasks (see {@link MoviesQueryTask} and {@link MoviesLocalQueryTask}).
     * <p>
     * Suitable in order to access activity's members (views, adapter, etc.)
     */
    private class MoviesQueryTaskListener implements AsyncTaskListener<Movie[]> {

        /**
         * Executed in the corresponding onPostExecute method of the AsyncTask.
         * Display result if there is any, otherwise display error message.
         *
         * @param movieArray Array of movies returned from the AsyncTask.
         */
        @Override
        public void onTaskComplete(Movie[] movieArray) {
            mLoadingIndicator.setVisibility(View.INVISIBLE);

            if (movieArray != null) {
                mRecyclerViewMovies.setVisibility(View.VISIBLE);
                mErrorMessageDisplay.setVisibility(View.INVISIBLE);
                mMovieAdapter.setMovieData(movieArray);
                //Restore the layout/position of the RecyclerView
                Log.d(LOG_TAG, "Trying to restore state of layout");
                mRecyclerViewMovies.getLayoutManager().onRestoreInstanceState(mRecyclerViewMoviesState);//restore
            } else {
                mRecyclerViewMovies.setVisibility(View.INVISIBLE);
                mErrorMessageDisplay.setText(getString(R.string.no_internet_access));
                mErrorMessageDisplay.setVisibility(View.VISIBLE);
            }
        }

        /**
         * Executed in the corresponding onPreExecute method of the AsyncTask.
         */
        @Override
        public void beforeTaskExecution() {
            mLoadingIndicator.setVisibility(View.VISIBLE);
        }

    }

    /**
     * Listener executed by onPreExecute and onPostExecute functionality of corresponding
     * AsyncTasks (see {@link MoviesQueryTask} and {@link MoviesLocalQueryTask}).
     * <p>
     * Suitable in order to access activity's members (views, adapter, etc.)
     */
    private class MoviesLocalQueryTaskListener implements AsyncTaskListener<Movie[]> {

        /**
         * Executed in the corresponding onPostExecute method of the AsyncTask.
         * Display result if there is any, otherwise display error message.
         *
         * @param movieArray Array of movies returned from the AsyncTask.
         */
        @Override
        public void onTaskComplete(Movie[] movieArray) {
            mLoadingIndicator.setVisibility(View.INVISIBLE);

            Log.d(LOG_TAG, "movieArray has size " + movieArray.length);

            if (movieArray != null && movieArray.length > 0) {
                mRecyclerViewMovies.setVisibility(View.VISIBLE);
                mErrorMessageDisplay.setVisibility(View.INVISIBLE);
                mMovieAdapter.setMovieData(movieArray);
                //Restore the layout/position of the RecyclerView
                Log.d(LOG_TAG, "Trying to restore state of layout");
                mRecyclerViewMovies.getLayoutManager().onRestoreInstanceState(mRecyclerViewMoviesState);//restore

            } else {
                mRecyclerViewMovies.setVisibility(View.INVISIBLE);
                mErrorMessageDisplay.setText(getString(R.string.no_favorites_in_list));
                mErrorMessageDisplay.setVisibility(View.VISIBLE);
            }
        }

        /**
         * Executed in the corresponding onPreExecute method of the AsyncTask.
         */
        @Override
        public void beforeTaskExecution() {
            mLoadingIndicator.setVisibility(View.VISIBLE);
        }

    }

}
