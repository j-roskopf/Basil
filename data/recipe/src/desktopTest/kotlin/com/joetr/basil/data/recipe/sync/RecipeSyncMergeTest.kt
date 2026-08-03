package com.joetr.basil.data.recipe.sync

import com.joetr.basil.network.RemoteRecipeRow
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeSyncMergeTest {
    private val remoteDelete = RemoteRecipeRow(
        title = "Soup",
        createdAt = 1L,
        deleted = true,
        updatedAt = 100L,
    )

    private val remoteUpdate = RemoteRecipeRow(
        title = "Soup",
        createdAt = 1L,
        deleted = false,
        updatedAt = 100L,
    )

    @Test
    fun remoteDeleteWinsOverStaleUpsert() {
        assertEquals(
            RemoteMergeAction.ApplyAndClearOutbox,
            remoteMergeAction(
                pendingKind = "UPSERT",
                localUpdatedAt = 200L,
                remote = remoteDelete,
            ),
        )
    }

    @Test
    fun remoteDeleteWinsOverNewerLocalTimestamp() {
        assertEquals(
            RemoteMergeAction.Apply,
            remoteMergeAction(
                pendingKind = null,
                localUpdatedAt = 200L,
                remote = remoteDelete,
            ),
        )
    }

    @Test
    fun pendingLocalDeleteBlocksRemoteDelete() {
        assertEquals(
            RemoteMergeAction.Skip,
            remoteMergeAction(
                pendingKind = "DELETE",
                localUpdatedAt = 50L,
                remote = remoteDelete,
            ),
        )
    }

    @Test
    fun pendingUpsertBlocksNonDeleteRemoteUpdate() {
        assertEquals(
            RemoteMergeAction.Skip,
            remoteMergeAction(
                pendingKind = "UPSERT",
                localUpdatedAt = 50L,
                remote = remoteUpdate,
            ),
        )
    }

    @Test
    fun newerLocalTimestampBlocksNonDeleteRemoteUpdate() {
        assertEquals(
            RemoteMergeAction.Skip,
            remoteMergeAction(
                pendingKind = null,
                localUpdatedAt = 200L,
                remote = remoteUpdate,
            ),
        )
    }

    @Test
    fun olderLocalTimestampAcceptsRemoteUpdate() {
        assertEquals(
            RemoteMergeAction.Apply,
            remoteMergeAction(
                pendingKind = null,
                localUpdatedAt = 50L,
                remote = remoteUpdate,
            ),
        )
    }
}
