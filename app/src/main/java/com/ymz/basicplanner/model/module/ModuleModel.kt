package com.ymz.basicplanner.model.module

import kotlinx.serialization.Serializable

/** What a module can be attached to. */
enum class ModuleTargetKind { DAY, SESSION, EXERCISE, SET }

@Serializable
data class ModuleInputSpec(
    /** Input name used by the module, e.g. "form". */
    val name: String,
    /** Type label shown in the UI (int, float, string, boolean). */
    val type: String = "int"
)

@Serializable
data class ModuleInputBinding(
    val specName: String,
    /** Entity whose items feed this input (the target itself or one of its ancestors). */
    val entityId: Long,
    val entityLabel: String = ""
)

/**
 * A Python function `apply(item)` that receives exactly one item value and returns the prescription
 * for every set it covers, as a tuple: `return sets, reps, percent, time`.
 *
 * [itemFamilyId] is the single item the function may read, picked when the module is created; the
 * engine resolves its value on the target (or the closest ancestor that carries it).
 */
@Serializable
data class ModuleDefinition(
    val id: Long,
    val name: String,
    val description: String = "",
    val code: String = DEFAULT_CODE,
    val itemFamilyId: Long? = null,
    val itemName: String = ""
) {
    companion object {
        const val DEFAULT_CODE: String =
            "def apply(item):\n" +
                "    # item is the value of the item you picked, already typed\n" +
                "    # return sets, reps, percent, time — or None to keep the plan\n" +
                "    if item >= 8:\n" +
                "        # drained: fewer sets, one rep less, lighter, longer rest\n" +
                "        return 3, 4, 70.0, 180\n" +
                "\n" +
                "    return None\n"

        /** What `apply` returns, in order. */
        const val RETURN_KEYS = "sets, reps, percent, time"
    }
}

/**
 * What `apply(item)` returns, once the tuple `sets, reps, percent, time` has been read back. Every
 * field is optional; a missing field means "leave the plan value alone".
 */
@Serializable
data class ModulePrescription(
    val sets: Int? = null,
    val reps: Int? = null,
    val percent: Double? = null,
    val time: Int? = null
) {
    fun isIdentity(): Boolean = sets == null && reps == null && percent == null && time == null
}

/**
 * Links a module to one target of one program. [programId] is what keeps two programs that share an
 * exercise apart: the same entity can carry different modules in different programs.
 */
@Serializable
data class ModuleAttachment(
    val id: Long,
    val moduleId: Long,
    val programId: Long,
    val targetKind: ModuleTargetKind,
    val targetId: Long,
    val bindings: List<ModuleInputBinding> = emptyList()
)

/** One set as the module sees it — plan values only, never the plan itself. */
@Serializable
data class ModuleSetSnapshot(
    val id: Long,
    val exerciseId: Long,
    val exercise: String,
    val movement: String,
    val reps: Int,
    val intensity: Int,
    val rest: Int,
    val weight_kg: Double
)

@Serializable
data class ModuleTargetInfo(
    val kind: String,
    val id: Long,
    val name: String
)

/** The one item a module reads: its family name, its type and the value found on the target or an ancestor. */
@Serializable
data class ModuleItemInfo(
    val name: String,
    val type: String = "string",
    val value: String
)

/** The JSON payload handed to `apply(ctx)`. */
@Serializable
data class ModuleContext(
    val date: String,
    val program_id: Long,
    val target: ModuleTargetInfo,
    /** The module's single item — what `apply` receives as its argument. */
    val item: ModuleItemInfo? = null,
    /** Item family name → value (kept for modules that still read the whole map). */
    val items: Map<String, String> = emptyMap(),
    val sets: List<ModuleSetSnapshot> = emptyList()
)

/** Adjustments a module asks for on one set; deltas so several modules can stack. */
data class SetAdjustment(
    val repsDelta: Int = 0,
    val percentDelta: Double = 0.0,
    val kgDelta: Double = 0.0,
    val restDelta: Int = 0
) {
    fun isIdentity(): Boolean =
        repsDelta == 0 && percentDelta == 0.0 && kgDelta == 0.0 && restDelta == 0
}

data class ModuleRunIssue(val moduleId: Long, val moduleName: String, val message: String)

data class ModuleOutcome(
    val adjustments: Map<Long, SetAdjustment> = emptyMap(),
    /** Prescription per exercise, so a module asking for 3 sets can be shown as such. */
    val prescriptions: Map<Long, ModulePrescription> = emptyMap(),
    val applied: List<Long> = emptyList(),
    val issues: List<ModuleRunIssue> = emptyList()
) {
    fun adjustmentFor(setEntryId: Long): SetAdjustment? = adjustments[setEntryId]
    fun isEmpty(): Boolean = adjustments.isEmpty() && issues.isEmpty()
}

/** Runs a module's Python source with the JSON context and returns its JSON output. */
fun interface ModuleExecutor {
    fun run(module: ModuleDefinition, contextJson: String): String
}
