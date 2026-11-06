package com.gitsync.minecraft.core.model

data class StorageDiagnostic(
    val androidOsVersion: Int,
    val targetSdkVersion: Int,
    val isExternalStorageLegacy: Boolean,
    val hasReadPermission: Boolean,
    val hasWritePermission: Boolean,
    val testedPath: String,
    val directoryExists: Boolean,
    val readAccessAvailable: Boolean,
    val worldEnumerationSuccessful: Boolean,
    val discoveredWorldsCount: Int,
    val safeWriteTestPassed: Boolean,
    val snapshotCreationSupported: Boolean,
    val safeReplacementSupported: Boolean,
    val automaticSyncEligibility: Boolean,
    val restrictionDetails: String?,
    val recommendedFallback: String,
    val timestampEpoch: Long = System.currentTimeMillis()
)
