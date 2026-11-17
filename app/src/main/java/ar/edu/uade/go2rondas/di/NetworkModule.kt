package ar.edu.uade.go2rondas.di

import ar.edu.uade.go2rondas.BuildConfig
import ar.edu.uade.go2rondas.core.network.MujocoServerAddress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // El ping detecta un socket muerto (por ejemplo, la PC del servidor se apagó) sin esperar al TCP.
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @MujocoServerAddress
    fun provideMujocoServerAddress(): String = BuildConfig.MUJOCO_SERVER_ADDRESS
}
