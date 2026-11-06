package com.gitsync.minecraft

import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.feature.backup.DivergenceDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class DivergenceDetectorTest {

    private fun makeCommit(id: String, parent: String?, sha: String, time: Long = 1000L): Commit {
        return Commit(
            commitId = id,
            worldId = "SurvivalWorld",
            parentCommitId = parent,
            timestamp = time,
            deviceId = "device-1",
            authorName = "Alex",
            commitMessage = "Commit $id",
            fileSizeBytes = 5000L,
            sha256Checksum = sha,
            snapshotFilePath = "/tmp/$id.mcworld",
            syncStatus = SyncStatus.SYNCED
        )
    }

    @Test
    fun testFastForwardProgression() {
        val root = makeCommit("c_root", null, "sha_root", 1000L)
        val incoming = makeCommit("c_v2", "c_root", "sha_v2", 2000L)

        val result = DivergenceDetector.evaluate(root, incoming, listOf(root))
        assertEquals(DivergenceDetector.SyncRelationship.FAST_FORWARD, result)
    }

    @Test
    fun testUpToDateCommit() {
        val root = makeCommit("c_root", null, "sha_root", 1000L)
        val incomingSame = makeCommit("c_root", null, "sha_root", 1000L)

        val result = DivergenceDetector.evaluate(root, incomingSame, listOf(root))
        assertEquals(DivergenceDetector.SyncRelationship.UP_TO_DATE, result)
    }

    @Test
    fun testDivergenceDetectionWhenBothAdvanceConcurrently() {
        val root = makeCommit("c_root", null, "sha_root", 1000L)
        // Player A advanced to commit A1 from root
        val localHead = makeCommit("c_a1", "c_root", "sha_a1", 2000L)
        // Player B advanced to commit B1 from root
        val incomingRemote = makeCommit("c_b1", "c_root", "sha_b1", 2100L)

        val result = DivergenceDetector.evaluate(localHead, incomingRemote, listOf(root, localHead))
        assertEquals(DivergenceDetector.SyncRelationship.DIVERGENT_BRANCH, result)
    }

    @Test
    fun testOutdatedRemoteCommitIgnored() {
        val root = makeCommit("c_root", null, "sha_root", 1000L)
        val v2 = makeCommit("c_v2", "c_root", "sha_v2", 2000L)
        val v3 = makeCommit("c_v3", "c_v2", "sha_v3", 3000L)

        // Local head is v3. Remote tries to send v2 (which is an ancestor of local head)
        val result = DivergenceDetector.evaluate(v3, v2, listOf(root, v2, v3))
        assertEquals(DivergenceDetector.SyncRelationship.OUTDATED_REMOTE, result)
    }
}
