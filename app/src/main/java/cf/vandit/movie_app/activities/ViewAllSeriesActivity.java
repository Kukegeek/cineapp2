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
import cf.vandit.movie_app.adapters.SeriesBriefSmallAdapter;
import cf.vandit.movie_app.network.series.OnTheAirSeriesResponse;
import cf.vandit.movie_app.network.series.PopularSeriesResponse;
import cf.vandit.movie_app.network.series.SeriesBrief;
import cf.vandit.movie_app.network.series.TopRatedSeriesResponse;
import cf.vandit.movie_app.request.ApiClient;
import cf.vandit.movie_app.request.ApiInterface;
import cf.vandit.movie_app.utils.Constants;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewAllSeriesActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private List<SeriesBrief> mSeries;
    private SeriesBriefSmallAdapter mSeriesAdapter;

    private int mSeriesType;
    private boolean pagesOver = false;
    private int presentPage = 1;
    private boolean loading = true;
    private int previousTotal = 0;
    private int visibleThreshold = 5;

    // Mapeo de constantes a títulos
    private static final SparseArray<String> TYPE_TITLES = new SparseArray<>();
    static {
        TYPE_TITLES.put(Constants.ON_THE_AIR_TV_SHOWS_TYPE, "On The Air Series");
        TYPE_TITLES.put(Constants.POPULAR_TV_SHOWS_TYPE, "Popular Series");
        TYPE_TITLES.put(Constants.TOP_RATED_TV_SHOWS_TYPE, "Top Rated Series");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_all_series);

        mSeriesType = getIntent().getIntExtra(Constants.VIEW_ALL_TV_SHOWS_TYPE, -1);
        if (mSeriesType == -1) {
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        loadSeries();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.view_series_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        String title = TYPE_TITLES.get(mSeriesType);
        if (title != null) setTitle(title);
    }

    private void setupRecyclerView() {
        mRecyclerView = findViewById(R.id.view_series_recView);
        mSeries = new ArrayList<>();
        mSeriesAdapter = new SeriesBriefSmallAdapter(mSeries, this);
        mRecyclerView.setAdapter(mSeriesAdapter);

        final GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();

                if (loading && totalCount > previousTotal) {
                    loading = false;
                    previousTotal = totalCount;
                }
                if (!loading && (totalCount - visibleCount) <= (firstVisible + visibleThreshold)) {
                    loadSeries();
                    loading = true;
                }
            }
        });
    }

    private void loadSeries() {
        if (pagesOver) return;

        ApiInterface api = ApiClient.getMovieApi();

        switch (mSeriesType) {
            case Constants.ON_THE_AIR_TV_SHOWS_TYPE:
                enqueueCall(
                    api.getOnTheAirSeries(Constants.API_KEY, presentPage),
                    new ResponseHandler<OnTheAirSeriesResponse>() {
                        @Override
                        public void onSuccess(OnTheAirSeriesResponse body) {
                            processResults(body.getResults(), body.getPage(), body.getTotalPages());
                        }
                    }
                );
                break;

            case Constants.POPULAR_TV_SHOWS_TYPE:
                enqueueCall(
                    api.getPopularSeries(Constants.API_KEY, presentPage),
                    new ResponseHandler<PopularSeriesResponse>() {
                        @Override
                        public void onSuccess(PopularSeriesResponse body) {
                            processResults(body.getResults(), body.getPage(), body.getTotalPages());
                        }
                    }
                );
                break;

            case Constants.TOP_RATED_TV_SHOWS_TYPE:
                enqueueCall(
                    api.getTopRatedSeries(Constants.API_KEY, presentPage),
                    new ResponseHandler<TopRatedSeriesResponse>() {
                        @Override
                        public void onSuccess(TopRatedSeriesResponse body) {
                            processResults(body.getResults(), body.getPage(), body.getTotalPages());
                        }
                    }
                );
                break;
        }
    }

    private <T> void enqueueCall(Call<T> call, ResponseHandler<T> handler) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> c, Response<T> response) {
                if (!response.isSuccessful()) {
                    c.clone().enqueue(this);
                    return;
                }
                if (response.body() != null) {
                    handler.onSuccess(response.body());
                }
            }
            @Override
            public void onFailure(Call<T> c, Throwable t) {
                // manejo de fallo si es necesario
            }
        });
    }

    private void processResults(List<SeriesBrief> results, int page, int totalPages) {
        if (results == null) return;
        for (SeriesBrief s : results) {
            if (s != null && s.getName() != null && s.getPosterPath() != null) {
                mSeries.add(s);
            }
        }
        mSeriesAdapter.notifyDataSetChanged();
        updatePagination(page, totalPages);
    }

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

    private interface ResponseHandler<T> {
        void onSuccess(T body);
    }
}
