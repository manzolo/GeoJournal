package it.manzolo.geojournal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.manzolo.geojournal.data.remote.FirebaseAuthRepositoryImpl
import it.manzolo.geojournal.data.repository.GeoPointRepositoryImpl
import it.manzolo.geojournal.domain.repository.AuthRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGeoPointRepository(
        impl: GeoPointRepositoryImpl
    ): GeoPointRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepositoryImpl
    ): AuthRepository
}
