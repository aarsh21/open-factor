package org.openfactor.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder

/** Configure the shared decoder before any screen, receiver or recreated activity requests art. */
class OpenFactorApplication : Application(), SingletonImageLoader.Factory {
    internal val exerciseLibrary = ExerciseLibraryRepository()
    override fun onCreate() {
        super.onCreate()
        exerciseLibrary.load(this)
    }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
