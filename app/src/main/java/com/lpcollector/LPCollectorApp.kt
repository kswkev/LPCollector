package com.lpcollector

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lpcollector.data.RecordRepository
import com.lpcollector.data.discogs.DiscogsClient
import com.lpcollector.data.images.CoverStore
import com.lpcollector.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class LPCollectorApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .build()
}

/** Hand-rolled dependency container; one instance per process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Discogs asks every client to identify itself with a User-Agent. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", DiscogsClient.USER_AGENT).build())
        }
        .build()

    val settings = SettingsRepository(app)
    val discogs = DiscogsClient(http) { settings.currentToken() }
    val covers = CoverStore(app.filesDir, http)
    val records = RecordRepository(app.filesDir, discogs, covers, scope)
}
