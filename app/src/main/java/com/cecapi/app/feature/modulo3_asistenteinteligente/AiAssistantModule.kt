package com.cecapi.app.feature.modulo3_asistenteinteligente

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiAssistantModule {
    @Binds
    @Singleton
    abstract fun bindAiAssistantApi(impl: ProxyAiAssistantApi): AiAssistantApi
}
