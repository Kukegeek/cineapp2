package cf.vandit.movie_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.util.SparseArray;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cf.vandit.movie_app.R;
import cf.vandit.movie_app.adapters.MovieBriefSmallAdapter;
import cf.vandit.movie_app.network.movie.GenreMoviesResponse;
import cf.vandit.movie_app.network.movie.MovieBrief;
import cf.vandit.movie_app.network.movie.PopularMoviesResponse;
import cf.vandit.movie_app.network.movie.TopRatedMoviesResponse;
import cf.vandit.movie_app.request.ApiClient;
import cf.vandit.movie_app.request.ApiInterface;
import cf.vandit.movie_app.utils.Constants;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewAllMoviesActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private List<MovieBrief> mMovies;
    private MovieBriefSmallAdapter mMoviesAdapter;

    private int mMovieType;
    private boolean pagesOver = false;
    private int presentPage = 1;
    private boolean loading = true;
    private int previousTotal = 0;
    private int visibleThreshold = 5;

    // Mapeo tipo → título para evitar el switch repetido
    private static final SparseArray<String> TYPE_TITLES = new SparseArray<>();
    static {
        TYPE_TITLES.put(Constants.POPULAR_MOVIES_TYPE,      "Popular Movies");
        TYPE_TITLES.put(Constants.TOP_RATED_MOVIES_TYPE,    "Top Rated Movies");
        TYPE_TITLES.put(Constants.ACTION_MOVIES_TYPE,       "Action Movies");
        TYPE_TITLES.put(Constants.ADVENTURE_MOVIES_TYPE,    "Adventure Movies");
        TYPE_TITLES.put(Constants.ANIMATION_MOVIES_TYPE,    "Animation Movies");
        TYPE_TITLES.put(Constants.COMEDY_MOVIES_TYPE,       "Comedy Movies");
        TYPE_TITLES.put(Constants.CRIME_MOVIES_TYPE,        "Crime Movies");
        TYPE_TITLES.put(Constants.DOCUMENTARY_MOVIES_TYPE,  "Documentary Movies");
        TYPE_TITLES.put(Constants.DRAMA_MOVIES_TYPE,        "Drama Movies");
        TYPE_TITLES.put(Constants.FAMILY_MOVIES_TYPE,       "Family Movies");
        TYPE_TITLES.put(Constants.FANTASY_MOVIES_TYPE,      "Fantasy Movies");
        TYPE_TITLES.put(Constants.HISTORY_MOVIES_TYPE,      "History Movies");
        TYPE_TITLES.put(Constants.HORROR_MOVIES_TYPE,       "Horror Movies");
        TYPE_TITLES.put(Constants.MUSIC_MOVIES_TYPE,        "Music Movies");
        TYPE_TITLES.put(Constants.MYSTERY_MOVIES_TYPE,      "Mystery Movies");
        TYPE_TITLES.put(Constants.ROMANCE_MOVIES_TYPE,      "Romance Movies");
        TYPE_TITLES.put(Constants.SCIFI_MOVIES_TYPE,        "Sci‑Fi Movies");
        TYPE_TITLES.put(Constants.TV_MOVIES_TYPE,           "TV Movies");
        TYPE_TITLES.put(Constants.THRILLER_MOVIES_TYPE,     "Thriller Movies");
        TYPE_TITLES.put(Constants.WAR_MOVIES_TYPE,          "War Movies");
        TYPE_TITLES.put(Constants.WESTERN_MOVIES_TYPE,      "Western Movies");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_all_movies);

        // Recogemos el tipo y, si es inválido, salimos
        mMovieType = getIntent().getIntExtra(Constants.VIEW_ALL_MOVIES_TYPE, -1);
        if (mMovieType == -1) {
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        loadMovies();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.view_movies_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        String title = TYPE_TITLES.get(mMovieType);
        if (title != null) setTitle(title);
    }

    private void setupRecyclerView() {
        mRecyclerView = findViewById(R.id.view_movies_recView);
        mMovies      = new ArrayList<>();
        mMoviesAdapter = new MovieBriefSmallAdapter(mMovies, this);

        final GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mMoviesAdapter);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                int visibleCount = layoutManager.getChildCount();
                int totalCount   = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();

                if (loading && totalCount > previousTotal) {
                    loading = false;
                    previousTotal = totalCount;
                }
                if (!loading && (totalCount - visibleCount) <= (firstVisible + visibleThreshold)) {
                    loadMovies();
                    loading = true;
                }
            }
        });
    }

    private void loadMovies() {
        if (pagesOver) return;

        ApiInterface api = ApiClient.getMovieApi();

        switch (mMovieType) {
            case Constants.POPULAR_MOVIES_TYPE:
                enqueueMovieCall(
                    api.getPopularMovies(Constants.API_KEY, presentPage),
                    new ResponseHandler<PopularMoviesResponse>() {
                        @Override
                        public void onSuccess(PopularMoviesResponse body) {
                            processGenericResults(
                                body.getResults(),
                                body.getPage(),
                                body.getTotalPages()
                            );
                        }
                    }
                );
                break;

            case Constants.TOP_RATED_MOVIES_TYPE:
                enqueueMovieCall(
                    api.getTopRatedMovies(Constants.API_KEY, presentPage, "US"),
                    new ResponseHandler<TopRatedMoviesResponse>() {
                        @Override
                        public void onSuccess(TopRatedMoviesResponse body) {
                            processGenericResults(
                                body.getResults(),
                                body.getPage(),
                                body.getTotalPages()
                            );
                        }
                    }
                );
                break;

            default:
                enqueueMovieCall(
                    api.getMoviesByGenre(Constants.API_KEY, mMovieType, presentPage),
                    new ResponseHandler<GenreMoviesResponse>() {
                        @Override
                        public void onSuccess(GenreMoviesResponse body) {
                            processGenreResults(
                                body.getResults(),
                                body.getPage(),
                                body.getTotalPages()
                            );
                        }
                    }
                );
                break;
        }
    }

    /**
     * Encola la llamada, reintentando automáticamente si la respuesta
     * no es exitosa, y delega el cuerpo a un handler genérico.
     */
    private <T> void enqueueMovieCall(Call<T> call, ResponseHandler<T> handler) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> c, Response<T> response) {
                if (!response.isSuccessful()) {
                    // retry automático
                    c.clone().enqueue(this);
                    return;
                }
                if (response.body() != null) {
                    handler.onSuccess(response.body());
                }
            }
            @Override
            public void onFailure(Call<T> c, Throwable t) {
                // aquí podrías loguear o mostrar un error
            }
        });
    }

    /**
     * Procesa los resultados de Popular y Top Rated:
     * filtra nulos y actualiza paginación.
     */
    private void processGenericResults(
        List<MovieBrief> results,
        int page,
        int totalPages
    ) {
        if (results == null) return;
        for (MovieBrief m : results) {
            if (m != null && m.getTitle() != null && m.getPosterPath() != null) {
                mMovies.add(m);
            }
        }
        mMoviesAdapter.notifyDataSetChanged();
        updatePagination(page, totalPages);
    }

    /**
     * Procesa los resultados de Género (solo comprueba posterPath).
     */
    private void processGenreResults(
        List<MovieBrief> results,
        int page,
        int totalPages
    ) {
        if (results == null) return;
        for (MovieBrief m : results) {
            if (m != null && m.getPosterPath() != null) {
                mMovies.add(m);
            }
        }
        mMoviesAdapter.notifyDataSetChanged();
        updatePagination(page, totalPages);
    }

    /** Actualiza los contadores de página y marca fin de páginas. */
    private void updatePagination(int page, int totalPages) {
        if (Objects.equals(page, totalPages)) {
            pagesOver = true;
        } else {
            presentPage++;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Callback genérico para manejar solo el cuerpo del Response. */
    private interface ResponseHandler<T> {
        void onSuccess(T body);
    }
}

