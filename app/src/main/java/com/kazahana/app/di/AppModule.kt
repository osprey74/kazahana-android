package com.kazahana.app.di

import android.content.Context
import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.PostRepository
import com.kazahana.app.data.repository.ThreadRepository
import com.kazahana.app.data.repository.TimelineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSessionStore(
        @ApplicationContext context: Context,
    ): SessionStore = SessionStore(context)

    @Provides
    @Singleton
    fun provideATProtoClient(
        sessionStore: SessionStore,
    ): ATProtoClient = ATProtoClient(sessionStore)

    @Provides
    @Singleton
    fun provideTimelineRepository(
        client: ATProtoClient,
    ): TimelineRepository = TimelineRepository(client)

    @Provides
    @Singleton
    fun providePostRepository(
        client: ATProtoClient,
    ): PostRepository = PostRepository(client)

    @Provides
    @Singleton
    fun provideInteractionRepository(
        client: ATProtoClient,
    ): InteractionRepository = InteractionRepository(client)

    @Provides
    @Singleton
    fun provideThreadRepository(
        client: ATProtoClient,
    ): ThreadRepository = ThreadRepository(client)
}
