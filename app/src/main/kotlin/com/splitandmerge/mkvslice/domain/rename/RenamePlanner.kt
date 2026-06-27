package com.splitandmerge.mkvslice.domain.rename

import java.util.UUID

enum class RenameDecision {
    RENAME,
    NO_CHANGE,
    SKIP_COLLISION,
    EXCLUDED_INVALID,
    EXCLUDED_UNRENAMABLE,
    EXCLUDED_UNVERIFIABLE
}

data class RenameInputItem(
    val id: String,
    val oldDisplayName: String,
    val newBaseName: String,
    val supportsRename: Boolean,
    val parentKnown: Boolean,
    val existingNamesInParent: List<String>,
    val parentKey: String = ""
)

data class RenamePlanResultItem(
    val id: String,
    val decision: RenameDecision,
    val targetName: String
)

object RenamePlanner {

    fun plan(
        inputs: List<RenameInputItem>,
        perRowAutoSuffix: Set<String> = emptySet()
    ): List<RenamePlanResultItem> {
        val fixedResults = mutableMapOf<String, RenamePlanResultItem>()

        // Group inputs by parentKey to enforce parent-scoping
        val inputsByParent = inputs.groupBy { it.parentKey }

        for ((parentKey, parentInputs) in inputsByParent) {
            // Build diskNames set for this parent folder (case-insensitive)
            val diskNames = parentInputs.flatMap { it.existingNamesInParent }
                .map { it.lowercase() }
                .toMutableSet()

            val renameCandidates = mutableListOf<RenameInputItem>()
            val nonRenameOriginals = mutableSetOf<String>()

            // 1. Initial categorization (validation, non-rename, etc.)
            for (input in parentInputs) {
                val oldName = input.oldDisplayName
                val dotIdx = oldName.lastIndexOf('.')
                val ext = if (dotIdx >= 0) oldName.substring(dotIdx) else ""
                val extLower = if (dotIdx >= 0) oldName.substring(dotIdx + 1).lowercase() else ""

                if (!input.parentKnown) {
                    fixedResults[input.id] = RenamePlanResultItem(input.id, RenameDecision.EXCLUDED_UNVERIFIABLE, oldName)
                    nonRenameOriginals.add(oldName.lowercase())
                } else if (!input.supportsRename) {
                    fixedResults[input.id] = RenamePlanResultItem(input.id, RenameDecision.EXCLUDED_UNRENAMABLE, oldName)
                    nonRenameOriginals.add(oldName.lowercase())
                } else {
                    val newBase = input.newBaseName
                    val hasIllegalChars = newBase.any { it in "<>:\"/\\|?*" }
                    val hasControlChars = newBase.any { it.code in 0..31 }
                    val hasLeadingTrailingSpaceDot = newBase.startsWith(" ") || newBase.endsWith(" ") || newBase.startsWith(".") || newBase.endsWith(".")
                    val isInvalidExt = extLower.isEmpty() || !VIDEO_EXTENSIONS.contains(extLower)

                    if (newBase.isEmpty() || newBase.length < 2 || hasIllegalChars || hasControlChars || hasLeadingTrailingSpaceDot || isInvalidExt) {
                        fixedResults[input.id] = RenamePlanResultItem(input.id, RenameDecision.EXCLUDED_INVALID, oldName)
                        nonRenameOriginals.add(oldName.lowercase())
                    } else {
                        val targetName = newBase + ext
                        if (targetName == oldName) {
                            fixedResults[input.id] = RenamePlanResultItem(input.id, RenameDecision.NO_CHANGE, oldName)
                            nonRenameOriginals.add(oldName.lowercase())
                        } else {
                            renameCandidates.add(input)
                        }
                    }
                }
            }

            // 2. Identify initial collisions (intra-batch and existing disk files)
            val initialTargetCounts = renameCandidates.groupingBy {
                val dotIdx = it.oldDisplayName.lastIndexOf('.')
                val ext = if (dotIdx >= 0) it.oldDisplayName.substring(dotIdx) else ""
                (it.newBaseName + ext).lowercase()
            }.eachCount()

            val activeCandidates = mutableListOf<RenameInputItem>()
            val skippedOriginals = mutableSetOf<String>()

            for (candidate in renameCandidates) {
                val oldName = candidate.oldDisplayName
                val dotIdx = oldName.lastIndexOf('.')
                val ext = if (dotIdx >= 0) oldName.substring(dotIdx) else ""
                val targetLower = (candidate.newBaseName + ext).lowercase()

                val diskCollision = targetLower in diskNames && targetLower != oldName.lowercase()
                val nonRenameCollision = targetLower in nonRenameOriginals && targetLower != oldName.lowercase()
                val intraBatchCollision = (initialTargetCounts[targetLower] ?: 0) > 1

                if (diskCollision || nonRenameCollision || intraBatchCollision) {
                    if (candidate.id !in perRowAutoSuffix) {
                        // Default to SKIP_COLLISION if the row did not opt-in to auto-suffixing
                        fixedResults[candidate.id] = RenamePlanResultItem(candidate.id, RenameDecision.SKIP_COLLISION, oldName)
                        skippedOriginals.add(oldName.lowercase())
                    } else {
                        activeCandidates.add(candidate)
                    }
                } else {
                    activeCandidates.add(candidate)
                }
            }

            // 3. Process active candidates sequentially
            val processedTargets = mutableSetOf<String>()
            val unprocessedInitialTargets = activeCandidates.associate {
                val dotIdx = it.oldDisplayName.lastIndexOf('.')
                val ext = if (dotIdx >= 0) it.oldDisplayName.substring(dotIdx) else ""
                it.id to (it.newBaseName + ext).lowercase()
            }.toMutableMap()

            for (candidate in activeCandidates) {
                val oldName = candidate.oldDisplayName
                val dotIdx = oldName.lastIndexOf('.')
                val ext = if (dotIdx >= 0) oldName.substring(dotIdx) else ""
                val base = candidate.newBaseName
                val initialTarget = base + ext
                val initialTargetLower = initialTarget.lowercase()

                unprocessedInitialTargets.remove(candidate.id)

                val currentOccupied = mutableSetOf<String>()
                currentOccupied.addAll(diskNames)
                currentOccupied.remove(oldName.lowercase())
                currentOccupied.addAll(nonRenameOriginals)
                currentOccupied.addAll(skippedOriginals)
                currentOccupied.addAll(processedTargets)

                // Only treat unprocessed targets as occupied if they are NOT in perRowAutoSuffix
                val unprocessedOccupied = unprocessedInitialTargets.filterKeys { it !in perRowAutoSuffix }.values
                currentOccupied.addAll(unprocessedOccupied)

                var finalTarget = initialTarget
                var finalTargetLower = initialTargetLower

                if (currentOccupied.contains(finalTargetLower)) {
                    if (candidate.id in perRowAutoSuffix) {
                        var suffixIndex = 1
                        var suffixTarget = "$base ($suffixIndex)$ext"
                        var suffixTargetLower = suffixTarget.lowercase()

                        while (currentOccupied.contains(suffixTargetLower)) {
                            suffixIndex++
                            suffixTarget = "$base ($suffixIndex)$ext"
                            suffixTargetLower = suffixTarget.lowercase()
                        }
                        finalTarget = suffixTarget
                        finalTargetLower = suffixTargetLower
                        
                        processedTargets.add(finalTargetLower)
                        fixedResults[candidate.id] = RenamePlanResultItem(candidate.id, RenameDecision.RENAME, finalTarget)
                    } else {
                        fixedResults[candidate.id] = RenamePlanResultItem(candidate.id, RenameDecision.SKIP_COLLISION, oldName)
                        skippedOriginals.add(oldName.lowercase())
                    }
                } else {
                    processedTargets.add(finalTargetLower)
                    fixedResults[candidate.id] = RenamePlanResultItem(candidate.id, RenameDecision.RENAME, finalTarget)
                }
            }
        }

        return inputs.map { fixedResults[it.id]!! }
    }
}
