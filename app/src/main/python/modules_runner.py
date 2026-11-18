import json

"""
Entry point for user modules.

run(code, ctx_json) executes the module source in a fresh namespace and calls apply(item), where
item is the value of the single item the module was created with, converted to its declared type
(int, float, boolean, string). Images are never offered as module inputs.

The module returns the prescription for the sets it covers, in this order:

    return sets, reps, percent, time

e.g.  return 3, 4, 70.0, 180    → 3 sets of 4 reps at 70% with 180 s rest
      return None               → leave the plan as written

Only the value is passed: no context dict, no plan.
"""


def _coerce(value, kind):
    """Turn the stored string into the item's declared type."""
    if value is None or value == "":
        return None
    kind = (kind or "string").lower()
    try:
        if kind == "int":
            return int(float(value))
        if kind == "float":
            return float(value)
        if kind == "boolean":
            return str(value).strip().lower() in ("1", "true", "yes", "y", "on")
    except (TypeError, ValueError):
        return value
    return value


def run(code, ctx_json):
    context = json.loads(ctx_json) if ctx_json else {}
    scope = {"__name__": "user_module"}
    exec(compile(code, "<module>", "exec"), scope)

    apply_fn = scope.get("apply")
    if apply_fn is None:
        raise ValueError("module has no apply(item) function")

    info = context.get("item") or {}
    item = _coerce(info.get("value"), info.get("type"))
    result = apply_fn(item)

    if result is None:
        return "null"
    if isinstance(result, (list, tuple)):
        return json.dumps(list(result))
    raise TypeError("apply(item) must return sets, reps, percent, time")
