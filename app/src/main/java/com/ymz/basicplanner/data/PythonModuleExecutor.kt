package com.ymz.basicplanner.data

import com.chaquo.python.Python
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleExecutor

/**
 * Runs module source with the embedded CPython (Chaquopy). `modules_runner.run(code, ctx_json)`
 * executes the module in a fresh namespace and calls `apply(ctx)`.
 *
 * The module is untrusted user code: it runs in-process, so failures are surfaced as exceptions and
 * the engine turns them into an issue badge instead of a crash, and the UI keeps the plan values.
 */
class PythonModuleExecutor : ModuleExecutor {

    private val python: Python by lazy { Python.getInstance() }

    override fun run(module: ModuleDefinition, contextJson: String): String {
        val runner = python.getModule("modules_runner")
        val result = runner.callAttr("run", module.code, contextJson)
        return result?.toString() ?: "[]"
    }
}
