package com.example.myapplication.presentation.di

import com.example.myapplication.domain.agent.ContextManager
import com.example.myapplication.domain.agent.LlmAgentFactory
import com.example.myapplication.domain.agent.LlmAgentFactoryImpl
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import com.example.myapplication.domain.repository.SettingsRepository
import com.example.myapplication.presentation.navigation.Navigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
object AgentModule {

    @Provides
    @ActivityRetainedScoped
    fun provideAgentFactory(
        repository: LlmRepository,
        historyRepository: ChatHistoryRepository,
        contextManager: ContextManager,
    ): LlmAgentFactory = LlmAgentFactoryImpl(repository, historyRepository, contextManager)

    @Provides
    @ActivityRetainedScoped
    fun provideNavigator(): Navigator = Navigator()
}
