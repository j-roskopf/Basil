package com.joetr.basil.data.recipe

import com.joetr.basil.data.recipe.sync.RecipeSyncService
import com.joetr.basil.domain.model.SyncState
import com.joetr.basil.domain.repository.SyncRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class DefaultSyncRepository(
    private val syncService: RecipeSyncService,
) : SyncRepository {
    override fun observeSyncState(): Flow<SyncState> = syncService.state

    override suspend fun syncNow() = syncService.syncNow()

    override suspend fun syncAfterSignIn() = syncService.syncAfterSignIn()

    override suspend fun retryFailed() = syncService.retryFailed()

    override suspend fun dropPendingSync() = syncService.dropPendingSync()

    override suspend fun dropPendingSyncEntry(id: String) = syncService.dropPendingSyncEntry(id)
}
