package org.openfactor.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Only an explicitly played clip opens a data source. The least-used bytes are evicted at 128 MiB. */
@UnstableApi
object ExerciseVideoCache {
    const val MAX_BYTES = 128L * 1024L * 1024L
    @Volatile private var instance: SimpleCache? = null

    @Synchronized
    private fun cache(context: Context): SimpleCache = instance ?: newCache(
        context, File(context.applicationContext.cacheDir, "exercise-stream-cache-v1"), MAX_BYTES,
    ).also { instance = it }

    internal fun newCache(context: Context, directory: File, maxBytes: Long): SimpleCache = SimpleCache(
        directory, LeastRecentlyUsedCacheEvictor(maxBytes), StandaloneDatabaseProvider(context.applicationContext),
    )

    fun dataSourceFactory(context: Context, location: ExerciseVideoLocation): DataSource.Factory {
        if (location.bundled) return DefaultDataSource.Factory(context.applicationContext)
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("OpenFactor/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(false)
        // Removed/full cache storage must not prevent otherwise playable streaming.
        return runCatching<DataSource.Factory> {
            CacheDataSource.Factory().setCache(cache(context)).setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrElse { upstream }
    }
}
