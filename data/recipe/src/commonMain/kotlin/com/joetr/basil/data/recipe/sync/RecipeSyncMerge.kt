package com.joetr.basil.data.recipe.sync

import com.joetr.basil.network.RemoteRecipeRow

internal enum class RemoteMergeAction {
  /** Keep local state; remote change is ignored. */
  Skip,

  /** Apply the remote recipe row as-is. */
  Apply,

  /** Drop a stale local outbox entry, then apply the remote recipe row. */
  ApplyAndClearOutbox,
}

/**
 * Decides whether a pulled remote recipe should be merged into the local database.
 *
 * Remote deletes always win over stale local upserts so cross-device deletions propagate.
 * Local outbox entries still block non-delete remote updates unless the remote is a delete.
 */
internal fun remoteMergeAction(
    pendingKind: String?,
    localUpdatedAt: Long?,
    remote: RemoteRecipeRow,
): RemoteMergeAction {
    if (remote.deleted) {
        if (pendingKind == "DELETE") return RemoteMergeAction.Skip
        if (pendingKind != null) return RemoteMergeAction.ApplyAndClearOutbox
        return RemoteMergeAction.Apply
    }
    if (pendingKind != null) return RemoteMergeAction.Skip
    if (localUpdatedAt != null && localUpdatedAt > remote.updatedAt) return RemoteMergeAction.Skip
    return RemoteMergeAction.Apply
}
