package it.manzolo.geojournal.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Placeholder per utility app-wide future.
// Database → DatabaseModule, Firebase → FirebaseModule,
// Repository → RepositoryModule, DataStore → DataStoreModule
@Module
@InstallIn(SingletonComponent::class)
object AppModule
