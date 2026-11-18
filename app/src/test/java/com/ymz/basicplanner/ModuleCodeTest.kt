package com.ymz.basicplanner

import com.ymz.basicplanner.model.module.ModuleDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prototype a new module starts from: `apply(<chosen item>)`. */
class ModuleCodeTest {

    @Test
    fun the_parameter_is_named_after_the_item() {
        assertEquals("max", ModuleDefinition.pythonParameterName("max"))
        assertEquals("max_effort", ModuleDefinition.pythonParameterName("Max effort"))
        assertEquals("note", ModuleDefinition.pythonParameterName("  Note "))
        assertTrue(ModuleDefinition.defaultCodeFor("Deadlift form", "max").startsWith("def deadlift_form(max):"))
    }

    @Test
    fun the_function_takes_the_module_name() {
        assertEquals("deadlift_form", ModuleDefinition.pythonFunctionName("Deadlift form"))
        assertEquals("module_1", ModuleDefinition.pythonFunctionName("Module 1"))
        assertEquals("apply", ModuleDefinition.pythonFunctionName("for"))
        assertEquals("apply", ModuleDefinition.pythonFunctionName("3x3"))
    }

    @Test
    fun unusable_names_fall_back_to_item() {
        assertEquals("item", ModuleDefinition.pythonParameterName("for"))
        assertEquals("item", ModuleDefinition.pythonParameterName("3x3"))
        assertEquals("item", ModuleDefinition.pythonParameterName(""))
        assertEquals("item", ModuleDefinition.pythonParameterName("!!!"))
    }

    @Test
    fun the_default_body_returns_four_deltas() {
        val code = ModuleDefinition.defaultCodeFor("Deadlift form", "note")
        assertTrue(code.contains("return -1, -1, -10.0, 60"))
        assertTrue(code.contains("return 0, 0, 0.0, 0"))
        assertTrue(code.contains("if note >= 8:"))
    }
}
