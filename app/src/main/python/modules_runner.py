import json

"""
Entry point for user modules.

run(code, ctx_json) executes the module source in a fresh namespace and calls its
apply(ctx) function. The context is a plain dict:

    {
      "date": "2026-08-03",
      "program_id": 70,
      "target": {"kind": "day", "id": 40, "name": "Monday"},
      "items": {"form": "7"},
      "sets": [{"id": 100, "exerciseId": 10, "exercise": "Medium", "movement": "Deadlift",
                "reps": 5, "intensity": 80, "rest": 120, "weight_kg": 160.0}]
    }

The module returns a list of per-set deltas, e.g.
    [{"set_id": 100, "reps_delta": -1}]
Accepted keys: set_id, reps_delta, percent_delta, kg_delta, rest_delta.
"""


def run(code, ctx_json):
    context = json.loads(ctx_json) if ctx_json else {}
    scope = {"__name__": "user_module"}
    exec(compile(code, "<module>", "exec"), scope)
    apply_fn = scope.get("apply")
    if apply_fn is None:
        raise ValueError("module has no apply(ctx) function")
    result = apply_fn(context)
    if result is None:
        return "[]"
    return json.dumps(result)
