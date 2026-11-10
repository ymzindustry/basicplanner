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
 * A Python function `apply(ctx)` plus the inputs it declares. Stored as source text so modules can
 * be written inside the app; [code] is executed by the platform executor (Chaquopy).
 */
@Serializable
data class ModuleDefinition(
    val id: Long,
    val name: String,
    val description: String = "",
    val code: String = DEFAULT_CODE,
    val inputs: List<ModuleInputSpec> = emptyList()
) {
    companion object {
        const val DEFAULT_CODE: String =
            "def apply(ctx):\n" +
                "    # ctx: date, program_id, target{kind,id,name}, items{name: value}, sets[]\n" +
                "    # return a list like [{\"set_id\": 12, \"reps_delta\": -1}]\n" +
                "    return []\n"
    }
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

/** The JSON payload handed to `apply(ctx)`. */
@Serializable
data class ModuleContext(
    val date: String,
    val program_id: Long,
    val target: ModuleTargetInfo,
    /** Item family name → value, for every entity bound to an input. */
    val items: Map<String, String>,
    val sets: List<ModuleSetSnapshot>
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
