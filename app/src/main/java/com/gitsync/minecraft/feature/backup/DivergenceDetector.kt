package com.gitsync.minecraft.feature.backup

import com.gitsync.minecraft.core.model.Commit

object DivergenceDetector {

    enum class SyncRelationship {
        UP_TO_DATE,       // Remote is identical or already present in local history
        FAST_FORWARD,     // Clean sequential progression (incoming parent == local head)
        OUTDATED_REMOTE,  // Remote is trying to push an older state that has already been superseded
        DIVERGENT_BRANCH  // Both local and remote have advanced from a common ancestor independently!
    }

    /**
     * Determines sync relationship between local head and incoming remote commit.
     * @param localHead The current latest commit on the local device.
     * @param incoming The commit received from the remote partner.
     * @param localHistory All known commits in local database for this world.
     */
    fun evaluate(
        localHead: Commit?,
        incoming: Commit,
        localHistory: List<Commit>
    ): SyncRelationship {
        if (localHead == null) {
            // First time receiving world
            return SyncRelationship.FAST_FORWARD
        }

        // 1. Identical checksum or commit ID
        if (localHead.commitId == incoming.commitId || localHead.sha256Checksum == incoming.sha256Checksum) {
            return SyncRelationship.UP_TO_DATE
        }

        // 2. Fast-Forward: Incoming parent is directly the current local head
        if (incoming.parentCommitId == localHead.commitId) {
            return SyncRelationship.FAST_FORWARD
        }

        // 3. Check if incoming commit is an ancestor of local head (remote sent older commit)
        if (isAncestorOf(potentialAncestorId = incoming.commitId, descendant = localHead, history = localHistory)) {
            return SyncRelationship.OUTDATED_REMOTE
        }

        // 4. Already present in local commit history
        if (localHistory.any { it.commitId == incoming.commitId }) {
            return SyncRelationship.UP_TO_DATE
        }

        // 5. If localHead was built on top of incoming's parent or they share common ancestor
        // and both devices diverged:
        return SyncRelationship.DIVERGENT_BRANCH
    }

    private fun isAncestorOf(
        potentialAncestorId: String,
        descendant: Commit,
        history: List<Commit>
    ): Boolean {
        var current: Commit? = descendant
        val historyMap = history.associateBy { it.commitId }

        while (current != null && current.parentCommitId != null) {
            if (current.parentCommitId == potentialAncestorId) {
                return true
            }
            current = historyMap[current.parentCommitId]
        }
        return false
    }
}
