package com.example.myapplication.presentation.di

import com.example.myapplication.data.repository.LlmRepositoryImpl
import com.example.myapplication.domain.agent.LlmAgent
import com.example.myapplication.domain.agent.LlmAgentFactory
import com.example.myapplication.domain.agent.LlmAgentImpl
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.repository.LlmRepository
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
    fun provideAgentFactory(repository: LlmRepository): LlmAgentFactory {
        return object : LlmAgentFactory {
            override fun create(model: String, config: GenerationConfig): LlmAgent {
                return LlmAgentImpl(repository, model, config)
            }
        }
    }

    @Provides
    @ActivityRetainedScoped
    fun provideNavigator(): Navigator = Navigator()
}
