package com.ikaru.BelajarHTTPResCache;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

;import static com.ikaru.BelajarHTTPResCache.JokesService.BASE_URL;
import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    Button btnGetRandomJoke;

    JokesService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        btnGetRandomJoke = findViewById(R.id.button);

        setupRetrofitAndOkHttp();

        if (isNetworkConnected()){
            Toast.makeText(this, "Connect", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show();
        }

        btnGetRandomJoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getRandomJokeFromAPI();

            }
        });

    }

    private void setupRetrofitAndOkHttp() {

        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        File httpCacheDirectory = new File(getCacheDir(), "offlineCache");

        //10 MB
        Cache cache = new Cache(httpCacheDirectory, 10 * 1024 * 1024);

        OkHttpClient client = new OkHttpClient().newBuilder()
                .cache(cache)
                .addNetworkInterceptor(REWRITE_RESPONSE_INTERCEPTOR)
                .addInterceptor(REWRITE_RESPONSE_INTERCEPTOR_OFFLINE)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .client(client)
                .baseUrl(BASE_URL)
                .build();

        apiService = retrofit.create(JokesService.class);

    }

    public void getRandomJokeFromAPI() {
        Observable<Jokes> observable = apiService.getRandomJoke("random");
        observable.subscribeOn(Schedulers.newThread()).
                observeOn(AndroidSchedulers.mainThread())
                .map(new Function<Jokes, String>() {
                    @Override
                    public String apply(Jokes jokes) throws Exception {
                        return jokes.getValue();
                    }
                }).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(String s) {
                textView.setText(s);
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(getApplicationContext(), "An error occurred in the Retrofit request. Perhaps no response/cache", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {

            }
        });

    }

    private final Interceptor REWRITE_RESPONSE_INTERCEPTOR = new Interceptor() {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Response originalResponse = chain.proceed(chain.request());
            String cacheControl = originalResponse.header("Cache-Control");
            if (cacheControl == null || cacheControl.contains("no-store") || cacheControl.contains("no-cache") ||
                    cacheControl.contains("must-revalidate") || cacheControl.contains("max-age=0")) {
                return originalResponse.newBuilder()
                        .removeHeader("Pragma")
                        //Cache Age 3600 = 1 hour
                        .header("Cache-Control", "public, max-age=" + 3600)
                        .build();
            } else {
                return originalResponse;
            }
        }
    };

    private final Interceptor REWRITE_RESPONSE_INTERCEPTOR_OFFLINE = new Interceptor() {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();


            //Do checking if there internet or not
            if (!isNetworkConnected()) {
                        //Remove this header if you try to Get Path
                        builder.removeHeader("Pragma")
                        //Reading from cache if there is no internet
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .header("Cache-Control", "public, only-if-cached")
                        .build();


            }else{
                    //Force the data get from internet if there is internet
                    builder.cacheControl(CacheControl.FORCE_NETWORK)
                            .build();
            }
            return chain.proceed(builder.build());
        }
    };


    //Checking the connection
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}